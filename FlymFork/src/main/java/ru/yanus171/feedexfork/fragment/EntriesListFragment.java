
/**
 * Flym
 * <p>
 * Copyright (c) 2012-2015 Frederic Julian
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ru.yanus171.feedexfork.fragment;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

import ru.yanus171.feedexfork.Constants;
import ru.yanus171.feedexfork.MainApplication;
import ru.yanus171.feedexfork.R;
import ru.yanus171.feedexfork.activity.BaseActivity;
import ru.yanus171.feedexfork.activity.EntryActivity;
import ru.yanus171.feedexfork.activity.HomeActivity;
import ru.yanus171.feedexfork.adapter.EntriesCursorAdapter;
import ru.yanus171.feedexfork.provider.FeedData;
import ru.yanus171.feedexfork.provider.FeedData.EntryColumns;
import ru.yanus171.feedexfork.provider.FeedDataContentProvider;
import ru.yanus171.feedexfork.service.FetcherService;
import ru.yanus171.feedexfork.utils.Dog;
import ru.yanus171.feedexfork.utils.PrefUtils;
import ru.yanus171.feedexfork.utils.Timer;
import ru.yanus171.feedexfork.utils.UiUtils;
import ru.yanus171.feedexfork.view.StatusText;
import ru.yanus171.feedexfork.view.TapZonePreviewPreference;

import static ru.yanus171.feedexfork.activity.BaseActivity.GetIsStatusBarHidden;
import static ru.yanus171.feedexfork.service.FetcherService.Status;
import static ru.yanus171.feedexfork.view.TapZonePreviewPreference.HideTapZonesText;

public class EntriesListFragment extends /*SwipeRefreshList*/Fragment {
    private static final String STATE_CURRENT_URI = "STATE_CURRENT_URI";
    private static final String STATE_ORIGINAL_URI = "STATE_ORIGINAL_URI";
    private static final String STATE_SHOW_FEED_INFO = "STATE_SHOW_FEED_INFO";
    //private static final String STATE_LIST_DISPLAY_DATE = "STATE_LIST_DISPLAY_DATE";
    private static final String STATE_SHOW_TEXT_IN_ENTRY_LIST = "STATE_SHOW_TEXT_IN_ENTRY_LIST";
    private static final String STATE_ORIGINAL_URI_SHOW_TEXT_IN_ENTRY_LIST = "STATE_ORIGINAL_URI_SHOW_TEXT_IN_ENTRY_LIST";
    private static final String STATE_SHOW_UNREAD = "STATE_SHOW_UNREAD";
    private static final String STATE_LAST_VISIBLE_ENTRY_ID = "STATE_LAST_VISIBLE_ENTRY_ID";
    private static final String STATE_LAST_VISIBLE_OFFSET = "STATE_LAST_VISIBLE_OFFSET";


    private static final int ENTRIES_LOADER_ID = 1;
    private static final int NEW_ENTRIES_NUMBER_LOADER_ID = 2;

    private Uri mCurrentUri, mOriginalUri;
    private boolean mOriginalUriShownEntryText = false;
    private boolean mShowFeedInfo = false;
    private boolean mShowTextInEntryList = false;
    private EntriesCursorAdapter mEntriesCursorAdapter;
    private Cursor mJustMarkedAsReadEntries;
    private FloatingActionButton mFab;
    public ListView mListView;
    private ProgressBar mProgressBar = null;
    public boolean mShowUnRead = false;
    private boolean mNeedSetSelection = false;
    private long mLastVisibleTopEntryID = 0;
    private int mLastListViewTopOffset = 0;
    private Menu mMenu = null;
    //private long mListDisplayDate = new Date().getTime();
    //boolean mBottomIsReached = false;
    private final ArrayList<String> mWasVisibleList = new ArrayList<>();


    private final LoaderManager.LoaderCallbacks<Cursor> mEntriesLoader = new LoaderManager.LoaderCallbacks<Cursor>() {
        @NonNull
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            Timer.Start( ENTRIES_LOADER_ID, "EntriesListFr.onCreateLoader" );

            String entriesOrder = PrefUtils.getBoolean(PrefUtils.DISPLAY_OLDEST_FIRST, false) || mShowTextInEntryList ? Constants.DB_ASC : Constants.DB_DESC;
            //String where = "(" + EntryColumns.FETCH_DATE + Constants.DB_IS_NULL + Constants.DB_OR + EntryColumns.FETCH_DATE + "<=" + mListDisplayDate + ')';
            String[] projection = mShowTextInEntryList ? EntryColumns.PROJECTION_WITH_TEXT : EntryColumns.PROJECTION_WITHOUT_TEXT;
            CursorLoader cursorLoader = new CursorLoader(getActivity(), mCurrentUri, projection, null, null, EntryColumns.DATE + entriesOrder);
            cursorLoader.setUpdateThrottle(150);
            Status().End( mStatus );
            mStatus = Status().Start( R.string.article_list_loading, true );
            return cursorLoader;
        }

        @Override
        public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
            Timer.End(ENTRIES_LOADER_ID);
            Timer timer = new Timer( "EntriesListFragment.onCreateLoader" );

            mEntriesCursorAdapter.swapCursor(data);
            if ( mShowTextInEntryList && mNeedSetSelection ) {
                mNeedSetSelection = false;
                mListView.setSelection(mEntriesCursorAdapter.GetFirstUnReadPos());
            }
            if ( mLastVisibleTopEntryID != -1 ) {
                int pos = mEntriesCursorAdapter.GetPosByID(mLastVisibleTopEntryID);
                if ( pos != -1 &&
                        ( pos > mListView.getLastVisiblePosition() || pos < mListView.getFirstVisiblePosition() )  )
                    mListView.setSelectionFromTop(pos, mLastListViewTopOffset);
            }
            getActivity().setProgressBarIndeterminateVisibility( false );
            Status().End( mStatus );
            timer.End();
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Cursor> loader) {
            Status().End( mStatus );
            //getActivity().setProgressBarIndeterminateVisibility( true );
            mEntriesCursorAdapter.swapCursor(Constants.EMPTY_CURSOR);
        }

    };

    private final OnSharedPreferenceChangeListener mPrefListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (PrefUtils.IS_REFRESHING.equals(key)) {
                UpdateActions();
            }
        }
    };
    private StatusText mStatusText = null;
    public static Uri mSearchQueryUri = null;
    private int mStatus = 0;

    private void UpdateActions() {
        if ( mMenu == null )
            return;

        if (EntryColumns.FAVORITES_CONTENT_URI.equals(mCurrentUri)) {
            mMenu.findItem(R.id.menu_refresh).setVisible(false);
        } else {
            mMenu.findItem(R.id.menu_share_starred).setVisible(true);
        }

        MenuItem item = mMenu.findItem( R.id.menu_toogle_toogle_unread_all );
        if (mShowUnRead) {
            item.setTitle(R.string.all_entries);
            item.setIcon(R.drawable.rounded_empty_white);
        } else {
            item.setTitle(R.string.unread_entries);
            item.setIcon(R.drawable.rounded_checbox_white);
        }

        if ( mCurrentUri != null ) {
            int uriMatch = FeedDataContentProvider.URI_MATCHER.match(mCurrentUri);
            item.setVisible(uriMatch != FeedDataContentProvider.URI_ENTRIES &&
                    uriMatch != FeedDataContentProvider.URI_UNREAD_ENTRIES &&
                    uriMatch != FeedDataContentProvider.URI_FAVORITES);
        }

        boolean isCanRefresh = !EntryColumns.FAVORITES_CONTENT_URI.equals( mCurrentUri );
        if ( mCurrentUri != null && mCurrentUri.getPathSegments().size() > 1 ) {
            String feedID = mCurrentUri.getPathSegments().get(1);
            isCanRefresh = !feedID.equals(FetcherService.GetExtrenalLinkFeedID());
        }
        boolean isRefresh = PrefUtils.getBoolean( PrefUtils.IS_REFRESHING, false );
        mMenu.findItem(R.id.menu_cancel_refresh).setVisible( isRefresh );
        mMenu.findItem(R.id.menu_refresh).setVisible( !isRefresh && isCanRefresh );


        if ( mProgressBar != null ) {
            if (isRefresh)
                mProgressBar.setVisibility(View.VISIBLE);
            else
                mProgressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Timer timer = new Timer( "EntriesListFragment.onCreate" );

        setHasOptionsMenu(true);

        Dog.v( "EntriesListFragment.onCreate" );

        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mCurrentUri = savedInstanceState.getParcelable(STATE_CURRENT_URI);
            mOriginalUri = savedInstanceState.getParcelable(STATE_ORIGINAL_URI);
            mOriginalUriShownEntryText = savedInstanceState.getBoolean(STATE_ORIGINAL_URI_SHOW_TEXT_IN_ENTRY_LIST);
            mShowFeedInfo = savedInstanceState.getBoolean(STATE_SHOW_FEED_INFO);
            mShowTextInEntryList = savedInstanceState.getBoolean(STATE_SHOW_TEXT_IN_ENTRY_LIST);
            mShowUnRead = savedInstanceState.getBoolean(STATE_SHOW_UNREAD, PrefUtils.getBoolean( STATE_SHOW_UNREAD, false ));
            Dog.v( String.format( "EntriesListFragment.onCreate mShowUnRead = %b", mShowUnRead ) );

            //if ( mShowTextInEntryList )
            //    mNeedSetSelection = true;
            mEntriesCursorAdapter = new EntriesCursorAdapter(getActivity(), mCurrentUri, Constants.EMPTY_CURSOR, mShowFeedInfo, mShowTextInEntryList, mShowUnRead);
        } else
            mShowUnRead = PrefUtils.getBoolean( STATE_SHOW_UNREAD, false );

        timer.End();
    }

    @Override
    public void onStart() {
        super.onStart();
        Timer timer = new Timer( "EntriesListFragment.onStart" );

        //refreshUI(); // Should not be useful, but it's a security
        //refreshSwipeProgress();
        PrefUtils.registerOnPrefChangeListener(mPrefListener);

        mFab = getActivity().findViewById(R.id.fab);
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                markAllAsRead();
            }
        });
        if ( !PrefUtils.getBoolean("show_mark_all_as_read_button", true) )
            mFab.hide();

        if (mCurrentUri != null) {
            // If the list is empty when we are going back here, try with the last display date
//            if (mNewEntriesNumber != 0 && mOldUnreadEntriesNumber == 0) {
//                mListDisplayDate = new Date().getTime();
//            } else {
//                mAutoRefreshDisplayDate = true; // We will try to update the list after if necessary
//            }
            restartLoaders();
        }
        mLastVisibleTopEntryID = PrefUtils.getLong( STATE_LAST_VISIBLE_ENTRY_ID, -1 );
        mLastListViewTopOffset = PrefUtils.getInt( STATE_LAST_VISIBLE_OFFSET, 0 );
        UpdateActions();
        timer.End();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private Uri GetUri(int pos) {
        final long id = mEntriesCursorAdapter.getItemId(pos);
        return mEntriesCursorAdapter.EntryUri(id);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Timer timer = new Timer( "EntriesListFragment.onCreateView" );

        View rootView = inflater.inflate(R.layout.fragment_entry_list, container, true);

        mStatusText  = new StatusText( (TextView)rootView.findViewById( R.id.statusText1 ),
                                       (TextView)rootView.findViewById( R.id.errorText ),
                                       (ProgressBar) rootView.findViewById( R.id.progressBarLoader),
                                       (TextView)rootView.findViewById( R.id.progressText ),
                                       Status());

        mProgressBar = rootView.findViewById(R.id.progressBar);
        mListView = rootView.findViewById(android.R.id.list);
        mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {

            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if ( mEntriesCursorAdapter == null )
                    return;
                for ( int i = firstVisibleItem; i < firstVisibleItem + visibleItemCount; i++ ) {
                    String uri = GetUri( i ).toString();
                    synchronized ( mWasVisibleList ) {
                        if (!mWasVisibleList.contains(uri))
                            mWasVisibleList.add(uri);
                    }
                }
                SetIsRead( firstVisibleItem - 2);
                if ( !mShowTextInEntryList && firstVisibleItem > 0 ) {
                    mLastVisibleTopEntryID = mEntriesCursorAdapter.getItemId(firstVisibleItem);
                    View v = mListView.getChildAt(0);
                    mLastListViewTopOffset = (v == null) ? 0 : (v.getTop() - mListView.getPaddingTop());
                }
            }
        });

        if (mEntriesCursorAdapter != null) {
            SetListViewAdapter();
        }

        if (PrefUtils.getBoolean(PrefUtils.DISPLAY_TIP, true) && mListView instanceof ListView ) {
            final TextView header = new TextView(mListView.getContext());
            header.setMinimumHeight(UiUtils.dpToPixel(70));
            int footerPadding = UiUtils.dpToPixel(10);
            header.setPadding(footerPadding, footerPadding, footerPadding, footerPadding);
            header.setText(R.string.tip_sentence);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setCompoundDrawablePadding(UiUtils.dpToPixel(5));
            header.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_about, 0, R.drawable.ic_action_cancel_gray, 0);
            header.setClickable(true);
            header.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mListView.removeHeaderView(header);
                    PrefUtils.putBoolean(PrefUtils.DISPLAY_TIP, false);
                }
            });
            mListView.addHeaderView(header);
        }

        if ( mListView instanceof ListView )
            UiUtils.addEmptyFooterView(mListView, 90);

        TextView emptyView = new TextView( getContext() );
        emptyView.setText( getString( R.string.no_entries ) );
        mListView.setEmptyView( emptyView );

        timer.End();
        return rootView;
    }




    private BaseActivity getBaseActivity() {
        return (BaseActivity) getActivity();
    }

    private void SetListViewAdapter() {


        mListView.setAdapter(mEntriesCursorAdapter);
        mNeedSetSelection = true;
    }




    private void SetIsRead(final int pos) {
        if ( !mShowTextInEntryList )
            return;
        final Uri uri = GetUri( pos );
        if ( EntriesCursorAdapter.mMarkAsReadList.contains( uri ) )
            return;
        class Run implements Runnable {
            private int mEntryPos;
            private Run(final int entryPos) {
                mEntryPos = entryPos;
            }
            @Override
            public void run() {
                if (mEntryPos < mListView.getFirstVisiblePosition() || mEntryPos > mListView.getLastVisiblePosition()) {
                    EntriesCursorAdapter.mMarkAsReadList.add(uri);
                    new Thread() {
                        @Override
                        public void run() {
                            ContentResolver cr = MainApplication.getContext().getContentResolver();
                            cr.update( uri, FeedData.getReadContentValues(), EntryColumns.WHERE_UNREAD, null);
                        }
                    }.start();
                }
            }
        }
        UiUtils.RunOnGuiThread(  new Run( pos ), 2000);

    }

    public static void ShowDeleteDialog(Context context, final String title, final long id) {
        new AlertDialog.Builder(context) //
                .setIcon(android.R.drawable.ic_dialog_alert) //
                .setTitle( R.string.question_delete_entry ) //
                .setMessage( title ) //
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new Thread() {
                            @Override
                            public void run() {
                                ContentResolver cr = MainApplication.getContext().getContentResolver();
                                cr.delete(EntryColumns.CONTENT_URI(id), null, null);
                            }
                        }.start();
                    }
                }).setNegativeButton(android.R.string.no, null).show();
    }


    @Override
    public void onStop() {
        PrefUtils.unregisterOnPrefChangeListener(mPrefListener);

        PrefUtils.putBoolean( STATE_SHOW_UNREAD, mShowUnRead );
        PrefUtils.putLong( STATE_LAST_VISIBLE_ENTRY_ID, mLastVisibleTopEntryID );
        PrefUtils.putInt( STATE_LAST_VISIBLE_OFFSET, mLastListViewTopOffset );


        if (mJustMarkedAsReadEntries != null && !mJustMarkedAsReadEntries.isClosed()) {
            mJustMarkedAsReadEntries.close();
        }

        synchronized ( mWasVisibleList ) {
            FetcherService.StartService(FetcherService.GetIntent(Constants.SET_VISIBLE_ITEMS_AS_OLD)
                                            .putStringArrayListExtra(Constants.URL_LIST, mWasVisibleList));
            mWasVisibleList.clear();
        }
        mFab = null;

        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(STATE_CURRENT_URI, mCurrentUri);
        outState.putParcelable(STATE_ORIGINAL_URI, mOriginalUri);
        outState.putBoolean(STATE_ORIGINAL_URI_SHOW_TEXT_IN_ENTRY_LIST, mOriginalUriShownEntryText);
        outState.putBoolean(STATE_SHOW_FEED_INFO, mShowFeedInfo);
        outState.putBoolean(STATE_SHOW_TEXT_IN_ENTRY_LIST, mShowTextInEntryList);
        //outState.putLong(STATE_LIST_DISPLAY_DATE, mListDisplayDate);
        outState.putBoolean(STATE_SHOW_UNREAD, mShowUnRead);

        super.onSaveInstanceState(outState);
    }

    /*@Override
    public void onRefresh() {
        startRefresh();
    }*/

    /*@Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        if (id >= 0) { // should not happen, but I had a crash with this on PlayStore...
            startActivity(new Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(mCurrentUri, id)));
        }
    }*/


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        mMenu = menu;
        menu.clear(); // This is needed to remove a bug on Android 4.0.3

        inflater.inflate(R.menu.entry_list, menu);

        final MenuItem searchItem = menu.findItem(R.id.menu_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        if (EntryColumns.isSearchUri(mCurrentUri)) {
            searchItem.expandActionView();
            searchView.post(new Runnable() { // Without that, it just does not work
                @Override
                public void run() {
                    searchView.setQuery(mCurrentUri.getLastPathSegment(), false);
                    searchView.clearFocus();
                }
            });
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (!TextUtils.isEmpty(newText))
                    setData(EntryColumns.SEARCH_URI(newText), true, true, false);
                return false;
            }
        });
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                setData(mOriginalUri, true, false, mOriginalUriShownEntryText);
                return false;
            }
        });


        UpdateActions();

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_share_starred: {
                if (mEntriesCursorAdapter != null) {
                    StringBuilder starredList = new StringBuilder();
                    Cursor cursor = mEntriesCursorAdapter.getCursor();
                    if (cursor != null && !cursor.isClosed()) {
                        int titlePos = cursor.getColumnIndex(EntryColumns.TITLE);
                        int linkPos = cursor.getColumnIndex(EntryColumns.LINK);
                        if (cursor.moveToFirst()) {
                            do {
                                starredList.append(cursor.getString(titlePos)).append("\n").append(cursor.getString(linkPos)).append("\n\n");
                            } while (cursor.moveToNext());
                        }
                        startActivity(Intent.createChooser(
                                new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_favorites_title))
                                        .putExtra(Intent.EXTRA_TEXT, starredList.toString()).setType(Constants.MIMETYPE_TEXT_PLAIN), getString(R.string.menu_share)
                        ));
                    }
                }
                return true;
            }

            case R.id.menu_refresh: {
                startRefresh();
                return true;
            }

            case R.id.menu_cancel_refresh: {
                FetcherService.cancelRefresh();
                return true;
            }

            case R.id.menu_toggle_theme: {
                PrefUtils.ToogleTheme( new Intent(getContext(), HomeActivity.class) );
                return true;
            }


            case R.id.menu_delete_all: {
                //if ( FeedDataContentProvider.URI_MATCHER.match(mCurrentUri) == FeedDataContentProvider.URI_ENTRIES_FOR_FEED ) {
                    new AlertDialog.Builder(getContext()) //
                            .setIcon(android.R.drawable.ic_dialog_alert) //
                            .setTitle( R.string.question ) //
                            .setMessage( R.string.deleteAllEntries ) //
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    new Thread() {
                                        @Override
                                        public void run() {
                                            FetcherService.deleteAllFeedEntries(mCurrentUri);
                                        }
                                    }.start();
                                }
                            }).setNegativeButton(android.R.string.no, null).show();
                //}
                return true;
            }
            case R.id.menu_mark_all_as_read: {
                markAllAsRead();
                return true;

            }
            case R.id.menu_create_auto_backup: {
                FetcherService.StartService( FetcherService.GetIntent( Constants.FROM_AUTO_BACKUP ) );
                return true;
            }
            case R.id.menu_add_feed_shortcut: {
                if ( ShortcutManagerCompat.isRequestPinShortcutSupported(getContext()) ) {
                    //Adding shortcut for MainActivity on Home screen

                    String name = "";
                    IconCompat image = null;
                    if ( EntryColumns.CONTENT_URI.equals(mCurrentUri) ) {
                        name = getContext().getString(R.string.all_entries);
                        image = IconCompat.createWithResource(getContext(), R.drawable.cup_empty);
                    } else if ( EntryColumns.FAVORITES_CONTENT_URI.equals(mCurrentUri) ) {
                        name = getContext().getString(R.string.favorites);
                        image = IconCompat.createWithResource( getContext(), R.drawable.cup_with_star );
                    } else if ( EntryColumns.UNREAD_ENTRIES_CONTENT_URI.equals(mCurrentUri) ) {
                        name = getContext().getString( R.string.unread_entries );
                        image = IconCompat.createWithResource(getContext(), R.mipmap.ic_launcher);
                    } else if ( FeedData.EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI( FetcherService.GetExtrenalLinkFeedID() ).equals(mCurrentUri) ) {
                        name = getContext().getString( R.string.externalLinks );
                        image = IconCompat.createWithResource(getContext(), R.drawable.load_later);
                    } else {
                        long feedID = Long.parseLong( mCurrentUri.getPathSegments().get(1) );
                        Cursor cursor = getContext().getContentResolver().query(FeedData.FeedColumns.CONTENT_URI(feedID),
                                new String[]{FeedData.FeedColumns.NAME, FeedData.FeedColumns.ICON},
                                null, null, null);
                        if (cursor.moveToFirst()) {
                            name = cursor.getString(0);
                            if (!cursor.isNull(1))
                                image = IconCompat.createWithBitmap(new BitmapDrawable(getContext().getResources(),
                                        UiUtils.getScaledBitmap(cursor.getBlob(1), 32)).getBitmap());
                        }
                        cursor.close();
                    }

                    ShortcutInfoCompat pinShortcutInfo = new ShortcutInfoCompat.Builder(getContext(), mCurrentUri.toString())
                            .setIcon(image)
                            .setShortLabel(name)
                            .setIntent(new Intent(getContext(), HomeActivity.class).setAction(Intent.ACTION_MAIN).setData( mCurrentUri ))
                            .build();
                    ShortcutManagerCompat.requestPinShortcut(getContext(), pinShortcutInfo, null);
                    if ( Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O )
                        Toast.makeText( getContext(), R.string.new_feed_shortcut_added, Toast.LENGTH_LONG ).show();
                } else
                    Toast.makeText( getContext(), R.string.new_feed_shortcut_add_failed, Toast.LENGTH_LONG ).show();
                return true;
            }
            case R.id.menu_toogle_toogle_unread_all: {
                int uriMatch = FeedDataContentProvider.URI_MATCHER.match(mCurrentUri);
                mShowUnRead = !mShowUnRead;
                if ( uriMatch == FeedDataContentProvider.URI_ENTRIES_FOR_FEED ||
                     uriMatch == FeedDataContentProvider.URI_UNREAD_ENTRIES_FOR_FEED ) {
                    long feedID = Long.parseLong( mCurrentUri.getPathSegments().get(1) );
                    Uri uri = mShowUnRead ? EntryColumns.UNREAD_ENTRIES_FOR_FEED_CONTENT_URI(feedID) : EntryColumns.ENTRIES_FOR_FEED_CONTENT_URI(feedID);
                    setData( uri, mShowFeedInfo, false, mShowTextInEntryList );
                } else if ( uriMatch == FeedDataContentProvider.URI_ENTRIES_FOR_GROUP ||
                            uriMatch == FeedDataContentProvider.URI_UNREAD_ENTRIES_FOR_GROUP ) {
                    long groupID = Long.parseLong( mCurrentUri.getPathSegments().get(1) );
                    Uri uri = mShowUnRead ? EntryColumns.UNREAD_ENTRIES_FOR_GROUP_CONTENT_URI(groupID) : EntryColumns.ENTRIES_FOR_GROUP_CONTENT_URI(groupID);
                    setData( uri, mShowFeedInfo, false, mShowTextInEntryList );
                }
                UpdateActions();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }


    @SuppressLint("PrivateResource")
    private void markAllAsRead() {
        if (mEntriesCursorAdapter != null) {
            Snackbar snackbar = Snackbar.make(getActivity().findViewById(R.id.coordinator_layout), R.string.marked_as_read, Snackbar.LENGTH_LONG)
                    .setActionTextColor(ContextCompat.getColor(getActivity(), R.color.light_theme_color_primary))
                    .setAction(R.string.undo, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new Thread() {
                                @Override
                                public void run() {
                                    if (mJustMarkedAsReadEntries != null && !mJustMarkedAsReadEntries.isClosed()) {
                                        ArrayList<Integer> ids = new ArrayList<>();
                                        while (mJustMarkedAsReadEntries.moveToNext()) {
                                            ids.add(mJustMarkedAsReadEntries.getInt(0));
                                        }
                                        ContentResolver cr = MainApplication.getContext().getContentResolver();
                                        String where = BaseColumns._ID + " IN (" + TextUtils.join(",", ids) + ')';
                                        cr.update(FeedData.EntryColumns.CONTENT_URI, FeedData.getUnreadContentValues(), where, null);

                                        mJustMarkedAsReadEntries.close();
                                    }
                                }
                            }.start();
                        }
                    });
            snackbar.getView().setBackgroundResource(R.color.material_grey_900);
            snackbar.show();

            new Thread() {
                @Override
                public void run() {
                    ContentResolver cr = MainApplication.getContext().getContentResolver();
                    //String where = EntryColumns.WHERE_UNREAD + Constants.DB_AND + '(' + EntryColumns.FETCH_DATE + Constants.DB_IS_NULL + Constants.DB_OR + EntryColumns.FETCH_DATE + "<=" + mListDisplayDate + ')';
                    String where = EntryColumns.WHERE_UNREAD;
                    if (mJustMarkedAsReadEntries != null && !mJustMarkedAsReadEntries.isClosed()) {
                        mJustMarkedAsReadEntries.close();
                    }
                    mJustMarkedAsReadEntries = cr.query(mCurrentUri, new String[]{BaseColumns._ID}, where, null, null);
                    if ( mCurrentUri != null && Constants.NOTIF_MGR != null  ) {
                        Constants.NOTIF_MGR.cancel( Constants.NOTIFICATION_ID_NEW_ITEMS_COUNT );
                        Constants.NOTIF_MGR.cancel( Constants.NOTIFICATION_ID_MANY_ITEMS_MARKED_STARRED );
                        if ( mJustMarkedAsReadEntries.moveToFirst() )
                            do {
                                Constants.NOTIF_MGR.cancel( mJustMarkedAsReadEntries.getInt(0) );
                            } while ( mJustMarkedAsReadEntries.moveToNext());
                        mJustMarkedAsReadEntries.moveToFirst();
                    }

                    cr.update(mCurrentUri, FeedData.getReadContentValues(), where, null);
                }
            }.start();


        }
    }

    private void startRefresh() {
        if ( mCurrentUri != null && !PrefUtils.getBoolean(PrefUtils.IS_REFRESHING, false)) {
            int uriMatcher = FeedDataContentProvider.URI_MATCHER.match(mCurrentUri);
            if ( uriMatcher == FeedDataContentProvider.URI_ENTRIES_FOR_FEED ||
                 uriMatcher == FeedDataContentProvider.URI_UNREAD_ENTRIES_FOR_FEED ) {
                FetcherService.StartService( new Intent(getActivity(), FetcherService.class)
                        .setAction(FetcherService.ACTION_REFRESH_FEEDS)
                        .putExtra(Constants.FEED_ID, mCurrentUri.getPathSegments().get(1)));
            } else if ( FeedDataContentProvider.URI_MATCHER.match(mCurrentUri) == FeedDataContentProvider.URI_ENTRIES_FOR_GROUP ) {
                FetcherService.StartService( new Intent(getActivity(), FetcherService.class)
                        .setAction(FetcherService.ACTION_REFRESH_FEEDS)
                        .putExtra(Constants.GROUP_ID, mCurrentUri.getPathSegments().get(1)));
            } else {
                FetcherService.StartService( new Intent(getActivity(), FetcherService.class)
                        .setAction(FetcherService.ACTION_REFRESH_FEEDS));
            }
        }

    }

    public Uri getUri() {
        return mOriginalUri;
    }

    public void setData(Uri uri, boolean showFeedInfo, boolean isSearchUri, boolean showTextInEntryList) {
        if ( getActivity() == null ) // during configuration changes
            return;
        Timer timer = new Timer( "EntriesListFragment.setData" );

        Dog.v( String.format( "EntriesListFragment.setData( %s )", uri.toString() ) );
        mCurrentUri = uri;
        if ( isSearchUri )
            mSearchQueryUri = uri;
        else  {
            mSearchQueryUri = null;
            mOriginalUri = mCurrentUri;
            mOriginalUriShownEntryText = showTextInEntryList;
        }

        mShowFeedInfo = showFeedInfo;
        mShowTextInEntryList = showTextInEntryList;
        new Thread() {
            @Override
            public void run() {
                synchronized ( mWasVisibleList ) {
                    SetVisibleItemsAsOld(mWasVisibleList);
                    mWasVisibleList.clear();
                }
            }
        }.start();
        mEntriesCursorAdapter = new EntriesCursorAdapter(getActivity(), mCurrentUri, Constants.EMPTY_CURSOR, mShowFeedInfo, mShowTextInEntryList, mShowUnRead);
        SetListViewAdapter();
        //if ( mListView instanceof ListView )
            mListView.setDividerHeight( mShowTextInEntryList ? 10 : 0 );
        //mListDisplayDate = new Date().getTime();
        if (mCurrentUri != null) {
            restartLoaders();
        }

        //refreshUI();
        mStatusText.SetFeedID( mCurrentUri );
        timer.End();
    }

    private void restartLoaders() {

        LoaderManager loaderManager = LoaderManager.getInstance( this );

        //HACK: 2 times to workaround a hard-to-reproduce bug with non-refreshing loaders...
        Timer.Start( ENTRIES_LOADER_ID, "EntriesListFr.restartLoaders() mEntriesLoader" );
        loaderManager.restartLoader(ENTRIES_LOADER_ID, null, mEntriesLoader);
        Timer.Start( NEW_ENTRIES_NUMBER_LOADER_ID, "EntriesListFr.restartLoaders() mEntriesNumberLoader" );
        //loaderManager.restartLoader(NEW_ENTRIES_NUMBER_LOADER_ID, null, mEntriesNumberLoader);

        loaderManager.restartLoader(ENTRIES_LOADER_ID, null, mEntriesLoader);
        //loaderManager.restartLoader(NEW_ENTRIES_NUMBER_LOADER_ID, null, mEntriesNumberLoader);
    }

    /*private void refreshUI() {
        if (mNewEntriesNumber > 0) {
            mRefreshListBtn.setText(getResources().getQuantityString(R.plurals.number_of_new_entries, mNewEntriesNumber, mNewEntriesNumber));
            mRefreshListBtn.setVisibility(View.VISIBLE);
        } else {
            mRefreshListBtn.setVisibility(View.GONE);
        }
    }*/
    public static void SetVisibleItemsAsOld(ArrayList<String> uriList) {
        final ArrayList<ContentProviderOperation> updates = new ArrayList<>();
        for (String uri : uriList)
            updates.add(
                ContentProviderOperation.newUpdate(Uri.parse( uri) )
                    .withValues(FeedData.getOldContentValues())
                    .withSelection(EntryColumns.WHERE_NEW, null)
                    .build());
        if (!updates.isEmpty()) {
            ContentResolver cr = MainApplication.getContext().getContentResolver();
            try {
                cr.applyBatch(FeedData.AUTHORITY, updates);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

}
