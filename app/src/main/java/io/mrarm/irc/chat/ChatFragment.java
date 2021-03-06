package io.mrarm.irc.chat;

import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.ImageViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;
import java.util.UUID;

import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.irc.ChannelNotificationManager;
import io.mrarm.irc.MainActivity;
import io.mrarm.irc.NotificationManager;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.ServerConnectionManager;
import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.config.ServerConfigManager;
import io.mrarm.irc.config.SettingsHelper;

public class ChatFragment extends Fragment implements
        ServerConnectionInfo.ChannelListChangeListener,
        ServerConnectionInfo.InfoChangeListener,
        NotificationManager.UnreadMessageCountCallback,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String ARG_SERVER_UUID = "server_uuid";
    public static final String ARG_CHANNEL_NAME = "channel";
    public static final String ARG_SEND_MESSAGE_TEXT = "message_text";

    private ServerConnectionInfo mConnectionInfo;

    private AppBarLayout mAppBar;
    private TabLayout mTabLayout;
    private ChatPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;
    private DrawerLayout mDrawerLayout;
    private ChatFragmentSendMessageHelper mSendHelper;
    private ChannelMembersAdapter mChannelMembersAdapter;
    private int mNormalToolbarInset;

    public static ChatFragment newInstance(ServerConnectionInfo server, String channel) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SERVER_UUID, server.getUUID().toString());
        if (channel != null)
            args.putString(ARG_CHANNEL_NAME, channel);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.chat_fragment, container, false);

        UUID connectionUUID = UUID.fromString(getArguments().getString(ARG_SERVER_UUID));
        mConnectionInfo = ServerConnectionManager.getInstance(getContext()).getConnection(connectionUUID);
        String requestedChannel = getArguments().getString(ARG_CHANNEL_NAME);

        if (mConnectionInfo == null) {
            ((MainActivity) getActivity()).openManageServers();
            return null;
        }

        mAppBar = rootView.findViewById(R.id.appbar);

        Toolbar toolbar = rootView.findViewById(R.id.toolbar);
        mNormalToolbarInset = toolbar.getContentInsetStartWithNavigation();

        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(mConnectionInfo.getName());

        ((MainActivity) getActivity()).addActionBarDrawerToggle(toolbar);

        mSectionsPagerAdapter = new ChatPagerAdapter(getContext(), getChildFragmentManager(), mConnectionInfo, savedInstanceState);

        mViewPager = rootView.findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        if (requestedChannel != null)
            setCurrentChannel(requestedChannel);

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i1) {
            }

            @Override
            public void onPageSelected(int i) {
                ((MainActivity) getActivity()).getDrawerHelper().setSelectedChannel(mConnectionInfo,
                        mSectionsPagerAdapter.getChannel(i));
            }

            @Override
            public void onPageScrollStateChanged(int i) {
            }
        });

        mConnectionInfo.addOnChannelListChangeListener(this);
        mConnectionInfo.addOnChannelInfoChangeListener(this);

        mTabLayout = rootView.findViewById(R.id.tabs);
        mTabLayout.setupWithViewPager(mViewPager, false);

        mSectionsPagerAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updateTabLayoutTabs();
            }
        });
        mConnectionInfo.getNotificationManager().addUnreadMessageCountCallback(this);
        updateTabLayoutTabs();

        mDrawerLayout = rootView.findViewById(R.id.drawer_layout);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        mChannelMembersAdapter = new ChannelMembersAdapter(mConnectionInfo, null);
        RecyclerView membersRecyclerView = rootView.findViewById(R.id.members_list);
        membersRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        membersRecyclerView.setAdapter(mChannelMembersAdapter);

        rootView.addOnLayoutChangeListener((View v, int left, int top, int right, int bottom,
                                            int oldLeft, int oldTop, int oldRight, int oldBottom) -> {
            int height = bottom - top;
            mAppBar.post(() -> {
                if (!isAdded())
                    return;
                if (height < getResources().getDimensionPixelSize(R.dimen.collapse_toolbar_activate_height)) {
                    mAppBar.setVisibility(View.GONE);
                } else {
                    updateToolbarCompactLayoutStatus(height);
                    mAppBar.setVisibility(View.VISIBLE);
                }
            });
        });
        mTabLayout.addOnLayoutChangeListener((View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) -> {
            if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom)
                return;
            mTabLayout.setScrollPosition(mTabLayout.getSelectedTabPosition(), 0.f, false);
        });

        mSendHelper = new ChatFragmentSendMessageHelper(this, rootView);
        String sendText = getArguments().getString(ARG_SEND_MESSAGE_TEXT);
        if (sendText != null)
            mSendHelper.setMessageText(sendText);

        SettingsHelper s = SettingsHelper.getInstance(getContext());
        s.addPreferenceChangeListener(SettingsHelper.PREF_CHAT_APPBAR_COMPACT_MODE, this);
        s.addPreferenceChangeListener(SettingsHelper.PREF_CHAT_TEXT_AUTOCORRECT, this);
        s.addPreferenceChangeListener(SettingsHelper.PREF_CHAT_FONT, this);
        s.addPreferenceChangeListener(SettingsHelper.PREF_NICK_AUTOCOMPLETE_SHOW_BUTTON, this);
        s.addPreferenceChangeListener(SettingsHelper.PREF_NICK_AUTOCOMPLETE_DOUBLE_TAP, this);

        mSendHelper.setTabButtonVisible(s.isNickAutocompleteButtonVisible());
        mSendHelper.setDoubleTapCompleteEnabled(s.isNickAutocompleteDoubleTapEnabled());
        mSendHelper.setMessageFieldTypeface(s.getChatFont());
        mSendHelper.setAutocorrectEnabled(s.isChatAutocorrectEnabled());

        return rootView;
    }

    private void updateTabLayoutTabs() {
        mTabLayout.removeAllTabs();
        final int c = mSectionsPagerAdapter.getCount();
        for (int i = 0; i < c; i++) {
            TabLayout.Tab tab = mTabLayout.newTab();
            tab.setText(mSectionsPagerAdapter.getPageTitle(i));
            tab.setTag(mSectionsPagerAdapter.getChannel(i));
            tab.setCustomView(R.layout.chat_tab);
            TextView textView = tab.getCustomView().findViewById(android.R.id.text1);
            textView.setTextColor(mTabLayout.getTabTextColors());
            ImageViewCompat.setImageTintList(tab.getCustomView().findViewById(R.id.notification_icon), mTabLayout.getTabTextColors());
            updateTabLayoutTab(tab);
            mTabLayout.addTab(tab, false);
        }

        final int currentItem = mViewPager.getCurrentItem();
        if (currentItem != mTabLayout.getSelectedTabPosition() && currentItem < mTabLayout.getTabCount())
            mTabLayout.getTabAt(currentItem).select();
    }

    private void updateTabLayoutTab(TabLayout.Tab tab) {
        String channel = (String) tab.getTag();
        boolean highlight = false;
        if (channel != null) {
            ChannelNotificationManager data = mConnectionInfo.getNotificationManager().getChannelManager(channel, false);
            if (data != null)
                highlight = data.hasUnreadMessages();
        }
        tab.getCustomView().findViewById(R.id.notification_icon).setVisibility(highlight ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (getView() != null) {
            updateToolbarCompactLayoutStatus(getView().getBottom() - getView().getTop());
            SettingsHelper s = SettingsHelper.getInstance(getContext());
            mSendHelper.setTabButtonVisible(s.isNickAutocompleteButtonVisible());
            mSendHelper.setDoubleTapCompleteEnabled(s.isNickAutocompleteDoubleTapEnabled());
            mSendHelper.setMessageFieldTypeface(s.getChatFont());
            mSendHelper.setAutocorrectEnabled(s.isChatAutocorrectEnabled());
        }
    }

    public void updateToolbarCompactLayoutStatus(int height) {
        String mode = SettingsHelper.getInstance(getContext()).getChatAppbarCompactMode();
        boolean enabled = mode.equals(SettingsHelper.COMPACT_MODE_ALWAYS) ||
                (mode.equals(SettingsHelper.COMPACT_MODE_AUTO) &&
                        height < getResources().getDimensionPixelSize(R.dimen.compact_toolbar_activate_height));
        setUseToolbarCompactLayout(enabled);
    }

    public void setUseToolbarCompactLayout(boolean enable) {
        Toolbar toolbar = ((MainActivity) getActivity()).getToolbar();
        if (enable == (mTabLayout.getParent() == toolbar))
            return;
        ((ViewGroup) mTabLayout.getParent()).removeView(mTabLayout);
        if (enable) {
            ViewGroup.LayoutParams params = new Toolbar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mTabLayout.setLayoutParams(params);
            toolbar.addView(mTabLayout);
            toolbar.setContentInsetStartWithNavigation(0);
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mTabLayout.setLayoutParams(params);
        } else {
            mAppBar.addView(mTabLayout);
            toolbar.setContentInsetStartWithNavigation(mNormalToolbarInset);
            ViewGroup.LayoutParams params = mTabLayout.getLayoutParams();
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mTabLayout.setLayoutParams(params);
        }
    }

    public void setTabsHidden(boolean hidden) {
        mTabLayout.setVisibility(hidden ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mConnectionInfo == null)
            return;
        mConnectionInfo.removeOnChannelListChangeListener(this);
        mConnectionInfo.removeOnChannelInfoChangeListener(this);
        mConnectionInfo.getNotificationManager().removeUnreadMessageCountCallback(this);
        SettingsHelper s = SettingsHelper.getInstance(getContext());
        s.removePreferenceChangeListener(SettingsHelper.PREF_CHAT_APPBAR_COMPACT_MODE, this);
        s.removePreferenceChangeListener(SettingsHelper.PREF_CHAT_TEXT_AUTOCORRECT, this);
        s.removePreferenceChangeListener(SettingsHelper.PREF_CHAT_FONT, this);
        s.removePreferenceChangeListener(SettingsHelper.PREF_NICK_AUTOCOMPLETE_SHOW_BUTTON, this);
        s.removePreferenceChangeListener(SettingsHelper.PREF_NICK_AUTOCOMPLETE_DOUBLE_TAP, this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSectionsPagerAdapter != null)
            mSectionsPagerAdapter.onSaveInstanceState(outState);
    }

    public ServerConnectionInfo getConnectionInfo() {
        return mConnectionInfo;
    }

    public void setCurrentChannel(String channel) {
        mViewPager.setCurrentItem(mSectionsPagerAdapter.findChannel(channel));
    }

    public void setCurrentChannelMembers(List<NickWithPrefix> members) {
        if (mChannelMembersAdapter == null)
            return;
        mChannelMembersAdapter.setMembers(members);
        mSendHelper.setCurrentChannelMembers(members);
        if (members == null || members.size() == 0)
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        else
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    public String getCurrentChannel() {
        return mSectionsPagerAdapter.getChannel(mViewPager.getCurrentItem());
    }

    public ChatFragmentSendMessageHelper getSendMessageHelper() {
        return mSendHelper;
    }

    @Override
    public void onConnectionInfoChanged(ServerConnectionInfo connection) {
        getActivity().runOnUiThread(() -> {
            mSendHelper.updateVisibility();
        });
    }

    @Override
    public void onChannelListChanged(ServerConnectionInfo connection, List<String> newChannels) {
        getActivity().runOnUiThread(() -> {
            mSectionsPagerAdapter.updateChannelList();
        });
    }

    @Override
    public void onUnreadMessageCountChanged(ServerConnectionInfo info, String channel,
                                            int messageCount, int oldMessageCount) {
        if (messageCount == 0 || (messageCount > 0 && oldMessageCount == 0)) {
            getActivity().runOnUiThread(() -> {
                int tabNumber = mSectionsPagerAdapter.findChannel(channel);
                TabLayout.Tab tab = mTabLayout.getTabAt(tabNumber);
                if (tab != null)
                    updateTabLayoutTab(tab);
            });
        }
    }

    public void closeDrawer() {
        mDrawerLayout.closeDrawer(GravityCompat.END, false);
    }

}
