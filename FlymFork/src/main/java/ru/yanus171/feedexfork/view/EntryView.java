/**
 * Flym
 * <p/>
 * Copyright (c) 2012-2015 Frederic Julian
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * <p/>
 * <p/>
 * Some parts of this software are based on "Sparse rss" under the MIT license (see
 * below). Please refers to the original project to identify which parts are under the
 * MIT license.
 * <p/>
 * Copyright (c) 2010-2012 Stefan Handschuh
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ru.yanus171.feedexfork.view;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;
import java.util.Stack;

import ru.yanus171.feedexfork.Constants;
import ru.yanus171.feedexfork.MainApplication;
import ru.yanus171.feedexfork.R;
import ru.yanus171.feedexfork.activity.EntryActivity;
import ru.yanus171.feedexfork.activity.LoadLinkLaterActivity;
import ru.yanus171.feedexfork.provider.FeedData;
import ru.yanus171.feedexfork.provider.FeedDataContentProvider;
import ru.yanus171.feedexfork.service.FetcherService;
import ru.yanus171.feedexfork.utils.ArticleTextExtractor;
import ru.yanus171.feedexfork.utils.Dog;
import ru.yanus171.feedexfork.utils.FileUtils;
import ru.yanus171.feedexfork.utils.HtmlUtils;
import ru.yanus171.feedexfork.utils.NetworkUtils;
import ru.yanus171.feedexfork.utils.PrefUtils;
import ru.yanus171.feedexfork.utils.Theme;
import ru.yanus171.feedexfork.utils.Timer;
import ru.yanus171.feedexfork.utils.UiUtils;

import static androidx.core.content.FileProvider.getUriForFile;
import static ru.yanus171.feedexfork.activity.BaseActivity.PAGE_SCROLL_DURATION_MSEC;
import static ru.yanus171.feedexfork.service.FetcherService.GetExtrenalLinkFeedID;
import static ru.yanus171.feedexfork.utils.ArticleTextExtractor.AddTagButtons;
import static ru.yanus171.feedexfork.utils.ArticleTextExtractor.FindBestElement;
import static ru.yanus171.feedexfork.utils.Theme.BUTTON_COLOR;
import static ru.yanus171.feedexfork.utils.Theme.LINK_COLOR;
import static ru.yanus171.feedexfork.utils.Theme.LINK_COLOR_BACKGROUND;
import static ru.yanus171.feedexfork.utils.Theme.QUOTE_BACKGROUND_COLOR;
import static ru.yanus171.feedexfork.utils.Theme.QUOTE_LEFT_COLOR;
import static ru.yanus171.feedexfork.utils.Theme.SUBTITLE_BORDER_COLOR;
import static ru.yanus171.feedexfork.utils.Theme.SUBTITLE_COLOR;

public class EntryView extends WebView implements Observer, Handler.Callback {

    private static final String TEXT_HTML = "text/html";
    private static final String HTML_IMG_REGEX = "(?i)<[/]?[ ]?img(.|\n)*?>";
    public static final String TAG = "EntryView";
    private static final String NO_MENU = "NO_MENU_";
    public static final String BASE_URL = "";
    private static final int CLICK_ON_WEBVIEW = 1;
    private static final int CLICK_ON_URL = 2;
    public static final int TOUCH_PRESS_POS_DELTA = 5;
    public boolean mWasAutoUnStar = false;

    private long mEntryId = -1;
    private String mEntryLink = "";
    public Runnable mScrollChangeListener = null;
    private int mLastContentLength = 0;
    private Stack<Integer> mHistoryAchorScrollY = new Stack<>();
    private final Handler mHandler = new Handler(this);
    private int mScrollY = 0;
    private int mStatus = 0;
    public boolean mLoadTitleOnly = false;
    public boolean mContentWasLoaded = false;
    private double mLastContentHeight = 0;
    private long mLastTimeScrolled = 0;
    private String mDataWithWebLinks = "";

    private static String GetCSS(String text) {
        return "<head><style type='text/css'> "
                + "body {max-width: 100%; margin: " + getMargins() + "; text-align:" + getAlign(text) + "; font-weight: " + getFontBold()
                + " color: " + Theme.GetTextColor() + "; background-color:" + Theme.GetBackgroundColor() + "; line-height: 120%} "
                + "* {max-width: 100%; word-break: break-word}"
                + "h1, h2 {font-weight: normal; line-height: 120%} "
                + "h1 {font-size: 140%; text-align:center; margin-top: 1.0cm; margin-bottom: 0.1em} "
                + "h2 {font-size: 120%} "
                + "a.no_draw_link {color: " + Theme.GetTextColor() + "; background: " + Theme.GetBackgroundColor() + "; text-decoration: none" + "}"
                + "a {color: " + Theme.GetColor(LINK_COLOR, R.string.default_link_color) + "; background: " + Theme.GetColor(LINK_COLOR_BACKGROUND, R.string.default_text_color_background) +
                (PrefUtils.getBoolean("underline_links", true) ? "" : "; text-decoration: none") + "}"
                + "h1 {color: inherit; text-decoration: none}"
                + "img {display: inline;max-width: 100%;height: auto; " + (PrefUtils.isImageWhiteBackground() ? "background: white" : "") + "} "
                + "iframe {allowfullscreen;position:relative;top:0;left:0;width:100%;height:100%;}"
                + "pre {white-space: pre-wrap;} "
                + "blockquote {border-left: thick solid " + Theme.GetColor(QUOTE_LEFT_COLOR, android.R.color.black) + "; background-color:" + Theme.GetColor(QUOTE_BACKGROUND_COLOR, android.R.color.black) + "; margin: 0.5em 0 0.5em 0em; padding: 0.5em} "
                + "td {font-weight: " + getFontBold() + "} "
                + "hr {width: 100%; color: #777777; align: center; size: 1} "
                + "p {margin: 0.8em 0 0.8em 0} "
                + "p.subtitle {color: " + Theme.GetColor(SUBTITLE_COLOR, android.R.color.black) + "; border-top:1px " + Theme.GetColor(SUBTITLE_BORDER_COLOR, android.R.color.black) + "; border-bottom:1px " + Theme.GetColor(SUBTITLE_BORDER_COLOR, android.R.color.black) + "; padding-top:2px; padding-bottom:2px; font-weight:800 } "
                + "ul, ol {margin: 0 0 0.8em 0.6em; padding: 0 0 0 1em} "
                + "ul li, ol li {margin: 0 0 0.8em 0; padding: 0} "
                + "div.button-section {padding: 0.4cm 0; margin: 0; text-align: center} "
                + ".button-section p {margin: 0.1cm 0 0.2cm 0} "
                + ".button-section p.marginfix {margin: 0.2cm 0 0.2cm 0}"
                + ".button-section input, .button-section a {font-family: sans-serif-light; font-size: 100%; color: #FFFFFF; background-color: " + Theme.GetColor(BUTTON_COLOR, android.R.color.black) + "; text-decoration: none; border: none; border-radius:0.2cm; padding: 0.3cm} "
                + ".tag_button i {font-family: sans-serif-light; font-size: 100%; color: #FFFFFF; background-color: " + Theme.GetColor(BUTTON_COLOR, android.R.color.black) + "; text-decoration: none; border: none; border-radius:0.2cm;  margin-right: 0.2cm; padding-top: 0.0cm; padding-bottom: 0.0cm; padding-left: 0.2cm; padding-right: 0.2cm} "
                + ".tag_button_full_text i {font-family: sans-serif-light; font-size: 100%; color: #FFFFFF; background-color: #00AA00; text-decoration: none; border: none; border-radius:0.2cm;  margin-right: 0.2cm; padding-top: 0.0cm; padding-bottom: 0.0cm; padding-left: 0.2cm; padding-right: 0.2cm} "
                + ".tag_button_hidden i {font-family: sans-serif-light; font-size: 100%; color: #FFFFFF; background-color: #888888; text-decoration: none; border: none; border-radius:0.2cm;  margin-right: 0.2cm; padding-top: 0.0cm; padding-bottom: 0.0cm; padding-left: 0.2cm; padding-right: 0.2cm} "
                + "</style><meta name='viewport' content='width=device-width'/></head>";
    }


    private static String getFontBold() {
        if (PrefUtils.getBoolean(PrefUtils.ENTRY_FONT_BOLD, false))
            return "bold;";
        else
            return "normal;";
    }

    private static String getMargins() {
        if (PrefUtils.getBoolean(PrefUtils.ENTRY_MAGRINS, true))
            return "4%";
        else
            return "0.1cm";
    }

    private static String getAlign(String text) {
        if (isTextRTL(text))
            return "right";
        else if (PrefUtils.getBoolean(PrefUtils.ENTRY_TEXT_ALIGN_JUSTIFY, false))
            return "justify";
        else
            return "left";
    }

    private static boolean isWordRTL(String s) {
        if (s.isEmpty()) {
            return false;
        }
        char c = s.charAt(0);
        return c >= 0x590 && c <= 0x6ff;
    }

    private static boolean isTextRTL(String text) {
        String[] list = TextUtils.split(text, " ");
        for (String item : list)
            if (isWordRTL(item))
                return true;
        return false;
    }

    public static boolean isRTL(String text) {
        final int directionality = Character.getDirectionality(Locale.getDefault().getDisplayName().charAt(0));
        return (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) && isTextRTL(text);
    }

    private static final String BODY_START = "<body dir=\"%s\">";
    private static final String BODY_END = "</body>";
    private static final String TITLE_START_WITH_LINK = "<h1><a class='no_draw_link' href=\"%s\">";
    private static final String TITLE_START = "<h1>";
    //private static final String TITLE_MIDDLE = "'>";
    private static final String TITLE_END = "</h1>";
    private static final String TITLE_END_WITH_LINK = "</a></h1>";
    private static final String SUBTITLE_START = "<p class='subtitle'>";
    private static final String SUBTITLE_END = "</p>";
    private static final String BUTTON_SECTION_START = "<div class='button-section'>";
    private static final String BUTTON_SECTION_END = "</div>";
    private static final String BUTTON_START = "<p><input type='button' value='";
    private static final String BUTTON_MIDDLE = "' onclick='";
    private static final String BUTTON_END = "'/></p>";
    // the separate 'marginfix' selector in the following is only needed because the CSS box model treats <input> and <a> elements differently
    private static final String LINK_BUTTON_START = "<p class='marginfix'><a href='";
    private static final String LINK_BUTTON_MIDDLE = "'>";
    private static final String LINK_BUTTON_END = "</a></p>";
    private static final String IMAGE_ENCLOSURE = "[@]image/";
    private static final long TAP_TIMEOUT = 300;
    private static final long MOVE_TIMEOUT = 800;

    private final JavaScriptObject mInjectedJSObject = new JavaScriptObject();
    private final ImageDownloadJavaScriptObject mImageDownloadObject = new ImageDownloadJavaScriptObject();
    public static final ImageDownloadObservable mImageDownloadObservable = new ImageDownloadObservable();
    private EntryViewManager mEntryViewMgr;
    String mData = "";
    public double mScrollPartY = 0;

    private EntryActivity mActivity;
    private long mPressedTime = 0;
    private long mMovedTime = 0;



    public EntryView(Context context) {
        super(context);
        init();
    }

    public EntryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EntryView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void setListener(EntryViewManager manager) {
        mEntryViewMgr = manager;
    }

    public boolean setHtml(final long entryId,
                           Cursor newCursor,
                           boolean isFullTextShown,
                           boolean forceUpdate,
                           EntryActivity activity) {
        Timer timer = new Timer("EntryView.setHtml");

        mEntryId = entryId;
        mEntryLink = newCursor.getString(newCursor.getColumnIndex(FeedData.EntryColumns.LINK));

        final String feedID = newCursor.getString(newCursor.getColumnIndex(FeedData.EntryColumns.FEED_ID));
        final String author = newCursor.getString(newCursor.getColumnIndex(FeedData.EntryColumns.AUTHOR));
        final long timestamp = newCursor.getLong(newCursor.getColumnIndex(FeedData.EntryColumns.DATE));
        final String feedTitle = newCursor.getString(newCursor.getColumnIndex(FeedData.FeedColumns.NAME));
        final String title =
            newCursor.getString(newCursor.getColumnIndex(FeedData.EntryColumns.TITLE)).replace( feedTitle, "" );
        final String enclosure = newCursor.getString(newCursor.getColumnIndex(FeedData.EntryColumns.ENCLOSURE));
        mWasAutoUnStar = newCursor.getInt(newCursor.getColumnIndex(FeedData.EntryColumns.IS_WAS_AUTO_UNSTAR)) == 1;
        mScrollPartY = !newCursor.isNull(newCursor.getColumnIndex(FeedData.EntryColumns.SCROLL_POS)) ?
                newCursor.getDouble(newCursor.getColumnIndex(FeedData.EntryColumns.SCROLL_POS)) : 0;

        String contentText;
        if (mLoadTitleOnly)
            contentText = getContext().getString(R.string.loading);
        else {
            try {
                if (!feedID.equals(GetExtrenalLinkFeedID()) &&
                        (!FileUtils.INSTANCE.isMobilized(mEntryLink, newCursor) || (forceUpdate && !isFullTextShown))) {
                    isFullTextShown = false;
                    contentText = newCursor.getString(newCursor.getColumnIndex(FeedData.EntryColumns.ABSTRACT));
                } else {
                    isFullTextShown = true;
                    contentText = FileUtils.INSTANCE.loadMobilizedHTML(mEntryLink, newCursor);
                }
                if (contentText == null) {
                    contentText = "";
                }

            } catch (IllegalStateException e) {
                e.printStackTrace();
                contentText = "Context too large";
            }
        }

        mActivity = activity;
        if (!mLoadTitleOnly && contentText.length() == mLastContentLength) {
            EndStatus();
            return isFullTextShown;
        }
        mLastContentLength = contentText.length();
        //getSettings().setBlockNetworkLoads(true);
        getSettings().setUseWideViewPort(true);
        getSettings().setSupportZoom(false);
        getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        if (PrefUtils.getBoolean(PrefUtils.DISPLAY_IMAGES, true)) {
            if (getSettings().getBlockNetworkImage()) {
                // setBlockNetworkImage(false) calls postSync, which takes time, so we clean up the html first and change the value afterwards
                loadData("", TEXT_HTML, Constants.UTF8);
                getSettings().setBlockNetworkImage(false);
            }
        } else {
            contentText = contentText.replaceAll(HTML_IMG_REGEX, "");
            getSettings().setBlockNetworkImage(true);
        }

        setBackgroundColor(Color.parseColor(Theme.GetBackgroundColor()));
        // Text zoom level from preferences
        int fontSize = PrefUtils.getFontSize();
        if (fontSize != 0) {
            getSettings().setTextZoom(100 + (fontSize * 20));
        }


        final String finalContentText = contentText;
        final boolean finalIsFullTextShown = isFullTextShown;
        new Thread() {
            @Override
            public void run() {
                synchronized (EntryView.this) {
                    mDataWithWebLinks = generateHtmlContent(feedID, title, mEntryLink, finalContentText, enclosure, author, timestamp, finalIsFullTextShown);
                    mData = mDataWithWebLinks;
                    if (PrefUtils.getBoolean(PrefUtils.DISPLAY_IMAGES, true))
                        mData = HtmlUtils.replaceImageURLs( mDataWithWebLinks, mEntryId, mEntryLink, true );
                }
                UiUtils.RunOnGuiThread(new Runnable() {
                    @Override
                    public void run() {
                        LoadData();
                    }
                });
            }
        }.start();
        timer.End();
        return isFullTextShown;
    }

    public void InvalidateContentCache() {
        mLastContentLength = 0;
    }

    private String generateHtmlContent(String feedID, String title, String link, String contentText, String enclosure, String author, long timestamp, boolean preferFullText) {
        Timer timer = new Timer("EntryView.generateHtmlContent");

        StringBuilder content = new StringBuilder(GetCSS(title)).append(String.format(BODY_START, isTextRTL(title) ? "rtl" : "inherit"));

        if (link == null) {
            link = "";
        }

        if (PrefUtils.getBoolean("entry_text_title_link", true))
            content.append(String.format(TITLE_START_WITH_LINK, link + NO_MENU)).append(title).append(TITLE_END_WITH_LINK);
        else
            content.append(TITLE_START).append(title).append(TITLE_END);

        content.append(SUBTITLE_START);

        Date date = new Date(timestamp);
        Context context = getContext();
        StringBuilder dateStringBuilder = new StringBuilder(DateFormat.getLongDateFormat(context).format(date)).append(' ').append(
                DateFormat.getTimeFormat(context).format(date));

        if (author != null && !author.isEmpty()) {
            dateStringBuilder.append(" &mdash; ").append(author);
        }

        content.append(dateStringBuilder).append(SUBTITLE_END).append(contentText);

        content.append(BUTTON_SECTION_START);
        if (!feedID.equals(FetcherService.GetExtrenalLinkFeedID())) {
            content.append(BUTTON_START);

            if (!preferFullText) {
                content.append(context.getString(R.string.get_full_text)).append(BUTTON_MIDDLE).append("injectedJSObject.onClickFullText();");
            } else {
                content.append(context.getString(R.string.original_text)).append(BUTTON_MIDDLE).append("injectedJSObject.onClickOriginalText();");
            }
            content.append(BUTTON_END);
        }

        if (preferFullText)
            content.append(BUTTON_START).append(context.getString(R.string.menu_reload_full_text)).append(BUTTON_MIDDLE)
                    .append("injectedJSObject.onReloadFullText();").append(BUTTON_END);
        if (enclosure != null && enclosure.length() > 6 && !enclosure.contains(IMAGE_ENCLOSURE)) {
            content.append(BUTTON_START).append(context.getString(R.string.see_enclosure)).append(BUTTON_MIDDLE)
                    .append("injectedJSObject.onClickEnclosure();").append(BUTTON_END);
        }
        content.append(BUTTON_START).append(context.getString(R.string.menu_go_back)).append(BUTTON_MIDDLE)
                .append("injectedJSObject.onClose();").append(BUTTON_END);
        /*if (link.length() > 0) {
            content.append(LINK_BUTTON_START).append(link).append(LINK_BUTTON_MIDDLE).append(context.getString(R.string.see_link)).append(LINK_BUTTON_END);
        }*/

        content.append(BUTTON_SECTION_END).append(BODY_END);

        timer.End();
        return content.toString();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void init() {

        StatusStartPageLoading();
        setBackgroundColor(Color.parseColor(Theme.GetBackgroundColor()));

        Timer timer = new Timer("EntryView.init");

        mImageDownloadObservable.addObserver(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            setTextDirection(TEXT_DIRECTION_LOCALE);
        // For scrolling
        setHorizontalScrollBarEnabled(false);
        setVerticalScrollBarEnabled(true);
        getSettings().setUseWideViewPort(true);
        // For color

        // For javascript
        getSettings().setJavaScriptEnabled(true);
        addJavascriptInterface(mInjectedJSObject, mInjectedJSObject.toString());
        addJavascriptInterface(mImageDownloadObject, mImageDownloadObject.toString());


        // For HTML5 video
        setWebChromeClient(new WebChromeClient() {
            private View mCustomView;
            private CustomViewCallback mCustomViewCallback;

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                // if a view already exists then immediately terminate the new one
                if (mCustomView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                FrameLayout videoLayout = mEntryViewMgr.getVideoLayout();
                if (videoLayout != null) {
                    mCustomView = view;

                    setVisibility(View.GONE);
                    videoLayout.setVisibility(View.VISIBLE);
                    videoLayout.addView(view);
                    mCustomViewCallback = callback;

                    mEntryViewMgr.onStartVideoFullScreen();
                }
            }

            @Override
            public void onHideCustomView() {
                super.onHideCustomView();

                if (mCustomView == null) {
                    return;
                }

                FrameLayout videoLayout = mEntryViewMgr.getVideoLayout();
                if (videoLayout != null) {
                    setVisibility(View.VISIBLE);
                    videoLayout.setVisibility(View.GONE);

                    // HideByScroll the custom view.
                    mCustomView.setVisibility(View.GONE);

                    // Remove the custom view from its container.
                    videoLayout.removeView(mCustomView);
                    mCustomViewCallback.onCustomViewHidden();

                    mCustomView = null;

                    mEntryViewMgr.onEndVideoFullScreen();
                }
            }

            @Override
            public Bitmap getDefaultVideoPoster() {
                Bitmap result = super.getDefaultVideoPoster();
                if (result == null)
                    result = BitmapFactory.decodeResource(MainApplication.getContext().getResources(), android.R.drawable.presence_video_online);
                return result;
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                //synchronized (EntryView.this) {
                    //if ( progress == 100 )
                    //    EndStatus();
                    //else if (mStatus != 0 )
                    //    FetcherService.Status().Change(mStatus, getContext().getString(R.string.web_page_loading) + " " + progress + " %");
                //}
            }


        });

        setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, final String url) {
                DoNotShowMenu();
                if ( System.currentTimeMillis() - mMovedTime < MOVE_TIMEOUT  )
                    return true;
                final Context context = getContext();
                try {
                    if (url.startsWith(Constants.FILE_SCHEME)) {
                        File file = new File(url.replace(Constants.FILE_SCHEME, ""));
                        File extTmpFile = new File(context.getCacheDir(), file.getName());
                        FileUtils.INSTANCE.copy(file, extTmpFile);
                        Uri contentUri = getUriForFile(getContext(), FeedData.PACKAGE_NAME + ".fileprovider", extTmpFile);
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setDataAndType(contentUri, "image/*");
                        context.startActivity(intent);
                    } else if (url.contains("#")) {
                        String hash = url.substring(url.indexOf('#') + 1);
                        hash = URLDecoder.decode(hash);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            view.evaluateJavascript("javascript:window.location.hash = '" + hash + "';", null);
                            mHistoryAchorScrollY.push(getScrollY());
                        }
                    } else if (url.contains(NO_MENU)) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        getContext().startActivity(intent.setData(Uri.parse(url.replace(NO_MENU, ""))));
                    } else {

                        class Item {
                            public final String text;
                            public final int icon;

                            private Item(int textID, Integer icon) {
                                this.text = getContext().getString(textID);
                                this.icon = icon;
                            }

                            @Override
                            public String toString() {
                                return text;
                            }
                        }

                        final Item[] items = {
                                new Item(R.string.loadLink, R.drawable.load_now),
                                new Item(R.string.loadLinkLater, R.drawable.load_later),
                                new Item(R.string.loadLinkLaterStarred, R.drawable.load_later_star),
                                new Item(R.string.open_link, android.R.drawable.ic_menu_send)
                        };

                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

                        builder.setAdapter(new ArrayAdapter<Item>(
                                getContext(),
                                android.R.layout.select_dialog_item,
                                android.R.id.text1,
                                items) {
                            @NonNull
                            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                                //Use super class to create the View
                                View v = super.getView(position, convertView, parent);
                                TextView tv = v.findViewById(android.R.id.text1);

                                //Put the image on the TextView
                                int dp50 = (int) (50 * getResources().getDisplayMetrics().density + 0.5f);
                                Drawable dr = getResources().getDrawable(items[position].icon);
                                Bitmap bitmap = ((BitmapDrawable) dr).getBitmap();
                                Drawable d = new BitmapDrawable(getResources(), Bitmap.createScaledBitmap(bitmap, dp50, dp50, true));
                                d.setBounds(0, 0, dp50, dp50);
                                tv.setCompoundDrawables(d, null, null, null);

                                //Add margin between image and text (support various screen densities)
                                int dp5 = (int) (5 * getResources().getDisplayMetrics().density + 0.5f);
                                tv.setCompoundDrawablePadding(dp5);

                                return v;
                            }
                        }, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                final Intent intent;
                                if (item == 0)
                                    intent = new Intent(getContext(), EntryActivity.class);
                                else if (item == 1)
                                    intent = new Intent(getContext(), LoadLinkLaterActivity.class);
                                else if (item == 2)
                                    intent = new Intent(getContext(), LoadLinkLaterActivity.class).putExtra(FetcherService.EXTRA_STAR, true );
                                else
                                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

                                getContext().startActivity(intent.setData(Uri.parse(url)));
                            }
                        });

                        builder.show();
                    }
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(context, R.string.cant_open_link, Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {

            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!mLoadTitleOnly)
                    mContentWasLoaded = true;
                else
                    EndStatus();
                //if ( mActivity.mEntryFragment.getCurrentEntryID() == mEntryId )
                    StatusStartPageLoading();
                ScheduleScrollTo(view);
            }

            private void ScheduleScrollTo(final WebView view) {
                Dog.v(TAG, "EntryView.ScheduleScrollTo() mEntryID = " + mEntryId + ", mScrollPartY=" + mScrollPartY + ", GetScrollY() = " + GetScrollY() + ", GetContentHeight()=" + GetContentHeight() );
                    double newContentHeight = GetContentHeight();
                    if (newContentHeight > 0 && newContentHeight == mLastContentHeight) {
                        ScrollToY();
                        EndStatus();
                    } else
                        view.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                ScheduleScrollTo(view);
                            }
                            // Delay the scrollTo to make it work
                        }, 150);
                    mLastContentHeight = newContentHeight;
            }
        });

        setOnTouchListener(new View.OnTouchListener() {
            private float mPressedY;
            private float mPressedX;

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    mPressedX = event.getX();
                    mPressedY = event.getY();
                    mPressedTime = System.currentTimeMillis();
                    //Log.v( TAG, "ACTION_DOWN mPressedTime=" + mPressedTime );
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(event.getX() - mPressedX) > TOUCH_PRESS_POS_DELTA ||
                            Math.abs(event.getY() - mPressedY) > TOUCH_PRESS_POS_DELTA) {
                        mPressedTime = 0;
                        mMovedTime = System.currentTimeMillis();
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    //Log.v( TAG, "ACTION_DOWN time delta=" + ( System.currentTimeMillis() - mPressedTime ) );
                    if ( System.currentTimeMillis() - mPressedTime < TAP_TIMEOUT &&
                            Math.abs(event.getX() - mPressedX) < TOUCH_PRESS_POS_DELTA &&
                            Math.abs(event.getY() - mPressedY) < TOUCH_PRESS_POS_DELTA &&
                            System.currentTimeMillis() - mLastTimeScrolled > 500 &&
                            PrefUtils.isArticleTapEnabled() &&
                            EntryActivity.GetIsActionBarHidden() &&
                            !mActivity.mHasSelection) {
                        //final HitTestResult hr = getHitTestResult();
                        //Log.v( TAG, "HitTestResult type=" + hr.getType() + ", extra=" + hr.getExtra()  );
                        mHandler.sendEmptyMessageDelayed(CLICK_ON_WEBVIEW, 300);
                    }
                }
                return false;
            }
        });


        timer.End();
    }

    private void EndStatus() {
        synchronized (EntryView.this) {
            if (mStatus != 0)
                FetcherService.Status().End(mStatus);
            mStatus = 0;
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == CLICK_ON_URL) {
            mHandler.removeMessages(CLICK_ON_WEBVIEW);
            mActivity.closeContextMenu();
            return true;
        }
        if (msg.what == CLICK_ON_WEBVIEW) {
            mActivity.openOptionsMenu();
            return true;
        }
        return false;
    }

    private void ScrollToY() {
        Dog.v(TAG, "EntryView.ScrollToY() mEntryID = " + mEntryId + ", mScrollPartY=" + mScrollPartY + ", GetScrollY() = " + GetScrollY());
        if (GetScrollY() > 0)
            EntryView.this.scrollTo(0, GetScrollY());
    }


    /*@Override
    public void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
        if ( scrollY > 50 && clampedY ) {
            mActivity.setFullScreen(false, EntryActivity.GetIsActionBarHidden());
        }

    }*/

    public String GetData() {
        synchronized (EntryView.this) {
            return mData;
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        FetcherService.Status().HideByScroll();
        //int contentHeight = (int) Math.floor(GetContentHeight());
        //int webViewHeight = getMeasuredHeight();
        mActivity.mEntryFragment.UpdateFooter();
        mLastTimeScrolled = System.currentTimeMillis();
        if (mScrollChangeListener != null)
            mScrollChangeListener.run();
    }

    public boolean IsScrollAtBottom() {
        return getScrollY() + getMeasuredHeight() >= (int) Math.floor(GetContentHeight()) - getMeasuredHeight() * 0.4;
    }

    @Override
    public void update(Observable observable, Object data) {
        if (data != null &&
                ((Entry) data).mID == mEntryId &&
                ((Entry) data).mLink.equals(mEntryLink)) {
            Dog.v("EntryView", "EntryView.update() " + mEntryId);
            UpdateImages( false );
        }
    }

    public void UpdateImages( final boolean downloadImages ) {
        StatusStartPageLoading();
        new Thread() {
            @Override
            public void run() {
                synchronized (EntryView.this) {
                    mData = HtmlUtils.replaceImageURLs( mDataWithWebLinks, mEntryId, mEntryLink, downloadImages);
                }
                UiUtils.RunOnGuiThread(new Runnable() {
                    @Override
                    public void run() {
                        if ( !IsStatusStartPageLoading() )
                            mScrollY = getScrollY();
                        LoadData();
                    }
                });
            }
        }.start();
    }

    public void UpdateTags() {
        final int status = FetcherService.Status().Start(getContext().getString(R.string.last_update), true);
        Document doc = Jsoup.parse(ArticleTextExtractor.mLastLoadedAllDoc, NetworkUtils.getUrlDomain(mEntryLink));
        Element root = FindBestElement(doc, mEntryLink, "", true);
        AddTagButtons(doc, mEntryLink, root);
        synchronized (EntryView.this) {
            mData = generateHtmlContent("-1", "", mEntryLink, doc.toString(), "", "", 0, true);
        }
        LoadData();
        FetcherService.Status().End(status);
    }

    private void LoadData() {
        if (mContentWasLoaded && GetViewScrollPartY() > 0)
            mScrollPartY = GetViewScrollPartY();
        //StatusStartPageLoading();
        synchronized (EntryView.this) {
            mLastContentHeight = 0;
            loadDataWithBaseURL(BASE_URL, mData, TEXT_HTML, Constants.UTF8, null);
        }
    }

    private final Runnable EndStatusRunnable = new Runnable() {
        @Override
        public void run() {
            EndStatus();
        }
    };
    public void StatusStartPageLoading() {
        synchronized (EntryView.this) {
            if (mStatus == 0) {
                mStatus = FetcherService.Status().Start(R.string.web_page_loading, true);
                UiUtils.RemoveFromGuiThread( EndStatusRunnable );
                UiUtils.RunOnGuiThread( EndStatusRunnable, 5000 );
            }
        }
    }
    private boolean IsStatusStartPageLoading() {
        synchronized (EntryView.this) {
            return mStatus == 0;
        }
    }

    static int NOTIFY_OBSERVERS_DELAY_MS = 1000;

    public static void NotifyToUpdate(final long entryId, final String entryLink) {
        UiUtils.RunOnGuiThread(new Runnable() {
            @Override
            public void run() {
                ScheduledNotifyObservers(entryId, entryLink);

            }
        }, NOTIFY_OBSERVERS_DELAY_MS);
    }

    static HashMap<Long, Long> mLastNotifyObserversTime = new HashMap<>();
    static HashMap<Long, Boolean> mLastNotifyObserversScheduled = new HashMap<>();

    static void ScheduledNotifyObservers(final long entryId, final String entryLink) {
        mLastNotifyObserversTime.put(entryId, new Date().getTime());
        if (!mLastNotifyObserversScheduled.containsKey(entryId)) {
            mLastNotifyObserversScheduled.put(entryId, true);
            UiUtils.RunOnGuiThread(new ScheduledEnrtyNotifyObservers(entryId, entryLink), NOTIFY_OBSERVERS_DELAY_MS);
        }
    }

    public boolean CanGoBack() {
        return canGoBack() && !mHistoryAchorScrollY.isEmpty();
    }

    public void GoBack() {
        if (CanGoBack())
            scrollTo(0, mHistoryAchorScrollY.pop());
    }

    public void GoTop() {
        mHistoryAchorScrollY.push(getScrollY());
        scrollTo(0, 0);
    }


    public interface EntryViewManager {
        void onClickOriginalText();

        void onClickFullText();

        void onClickEnclosure();

        void onReloadFullText();

        void onClose();

        void onStartVideoFullScreen();

        void onEndVideoFullScreen();

        FrameLayout getVideoLayout();

        void downloadImage(String url);

        void openTagMenu(String className, String baseUrl, String paramValue);

        void downloadNextImages();

        void downloadAllImages();
    }

    private class JavaScriptObject {
        @Override
        @JavascriptInterface
        public String toString() {
            return "injectedJSObject";
        }

        @JavascriptInterface
        public void onClickOriginalText() {
            DoNotShowMenu();
            mEntryViewMgr.onClickOriginalText();
        }

        @JavascriptInterface
        public void onClickFullText() {
            DoNotShowMenu();
            mEntryViewMgr.onClickFullText();
        }

        @JavascriptInterface
        public void onClickEnclosure() {
            DoNotShowMenu();
            mEntryViewMgr.onClickEnclosure();
        }

        @JavascriptInterface
        public void onReloadFullText() {
            DoNotShowMenu();
            mEntryViewMgr.onReloadFullText();
        }

        @JavascriptInterface
        public void onClose() {
            DoNotShowMenu();
            mEntryViewMgr.onClose();
        }
    }

    private void DoNotShowMenu() {
        mHandler.sendEmptyMessage(CLICK_ON_URL);
        mActivity.closeOptionsMenu();
    }

    private class ImageDownloadJavaScriptObject {
        @Override
        @JavascriptInterface
        public String toString() {
            return "ImageDownloadJavaScriptObject";
        }

        @JavascriptInterface
        public void downloadImage(String url) {
            DoNotShowMenu();
            mEntryViewMgr.downloadImage(url);
        }

        @JavascriptInterface
        public void downloadNextImages() {
            DoNotShowMenu();
            mEntryViewMgr.downloadNextImages();

        }

        @JavascriptInterface
        public void downloadAllImages() {
            DoNotShowMenu();
            mEntryViewMgr.downloadAllImages();

        }

        @JavascriptInterface
        public void openTagMenu(String className, String baseUrl, String paramValue) {
            DoNotShowMenu();
            mEntryViewMgr.openTagMenu(className, baseUrl, paramValue);
        }
    }

    public static class ImageDownloadObservable extends Observable {
        @Override
        public boolean hasChanged() {
            return true;
        }
    }


    private int GetScrollY() {
        if (mScrollY != 0)
            return mScrollY;
        return GetContentHeight() * mScrollPartY != 0 ? (int) (GetContentHeight() * mScrollPartY) : 0;
    }

    public double GetContentHeight() {
        return getContentHeight() * getScale();
    }

    public double GetViewScrollPartY() {
        return getContentHeight() != 0 ? getScrollY() / GetContentHeight() : 0;
    }

    public void PageChange(int delta, StatusText statusText) {
        ObjectAnimator anim = ObjectAnimator.ofInt(this, "scrollY", getScrollY(),
                (int) (getScrollY() + delta * (getHeight() - statusText.GetHeight()) *
                        (PrefUtils.getBoolean("page_up_down_90_pct", false) ? 0.9 : 0.98)));
        anim.setDuration(PAGE_SCROLL_DURATION_MSEC);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        Dog.d( "WebView.onPause " + mEntryId );
        SaveScrollPos();
        EndStatus();
    }

    public void SaveScrollPos() {
        if ( !mContentWasLoaded )
            return;
        mScrollPartY = GetViewScrollPartY();
        if ( mScrollPartY > 0.0001 ) {
            Dog.v(TAG, String.format("EnrtyView.SaveScrollPos (entry %d) mScrollPartY = %f getScrollY() = %d, view.getContentHeight() = %f", mEntryId, mScrollPartY, getScrollY(), GetContentHeight()));
//            new Thread() {
//                @Override
//                public void run() {
                    ContentValues values = new ContentValues();
                    values.put(FeedData.EntryColumns.SCROLL_POS, mScrollPartY);
                    ContentResolver cr = MainApplication.getContext().getContentResolver();
                    FeedDataContentProvider.mNotifyEnabled = false;
                    //String where = FeedData.EntryColumns.SCROLL_POS + " < " + scrollPart + Constants.DB_OR + FeedData.EntryColumns.SCROLL_POS + Constants.DB_IS_NULL;
                    cr.update(FeedData.EntryColumns.CONTENT_URI(mEntryId), values, null, null);
                    FeedDataContentProvider.mNotifyEnabled = true;
                    //Dog.v("EntryView", String.format("EntryView.SaveScrollPos (entry %d) update scrollPos = %f", mEntryId, mScrollPartY));
//                }
//            }.start();
        }
    }

}

class ScheduledEnrtyNotifyObservers implements Runnable {
    private final String mLink;
    private long mId = 0;

    public ScheduledEnrtyNotifyObservers( long id, String link ) {
        mId = id;
        mLink = link;
    }

    @Override
    public void run() {
        EntryView.mLastNotifyObserversScheduled.remove( mId );
        Dog.v( EntryView.TAG,"EntryView.ScheduledNotifyObservers() run");
        if (new Date().getTime() - EntryView.mLastNotifyObserversTime.get( mId ) > EntryView.NOTIFY_OBSERVERS_DELAY_MS)
            EntryView.mImageDownloadObservable.notifyObservers(new Entry(mId, mLink) );
        else
            EntryView.ScheduledNotifyObservers( mId, mLink );
    }
}
