/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settingslib.collapsingtoolbar;

import static android.text.Layout.HYPHENATION_FREQUENCY_NORMAL_FAST;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.text.LineBreakConfig;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toolbar;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.android.settingslib.collapsingtoolbar.widget.ScrollableToolbarItemLayout;
import com.android.settingslib.widget.SettingsThemeHelper;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingtoolbar.FloatingToolbarLayout;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import java.util.List;

/**
 * A delegate that allows to use the collapsing toolbar layout in hosts that doesn't want/need to
 * extend from {@link CollapsingToolbarBaseActivity} or from {@link CollapsingToolbarBaseFragment}.
 */
public class CollapsingToolbarDelegate {
    private static final String TAG = "CTBdelegate";
    /** Interface to be implemented by the host of the Collapsing Toolbar. */
    public interface HostCallback {
        /**
         * Called when a Toolbar should be set on the host.
         *
         * <p>If the host wants action bar to be modified, it should return it.
         */
        @Nullable
        ActionBar setActionBar(Toolbar toolbar);

        /** Sets support tool bar and return support action bar, this is for AppCompatActivity. */
        @Nullable
        default androidx.appcompat.app.ActionBar setActionBar(
                androidx.appcompat.widget.Toolbar toolbar) {
            return null;
        }

        /** Sets a title on the host. */
        void setOuterTitle(CharSequence title);
    }

    private static final float TOOLBAR_LINE_SPACING_MULTIPLIER = 1.1f;

    @Nullable
    private CoordinatorLayout mCoordinatorLayout;
    @Nullable
    private CollapsingToolbarLayout mCollapsingToolbarLayout;
    @Nullable
    private AppBarLayout mAppBarLayout;
    @NonNull
    private Toolbar mToolbar;
    @Nullable
    private View mToolbarButtonsContainer;
    @Nullable
    private MaterialButton mPrimaryButton;
    @Nullable
    private MaterialButton mSecondaryButton;
    @Nullable
    private MaterialButton mActionButton;
    @Nullable
    private MaterialButton mActionIconOnlyButton;
    @NonNull
    private FrameLayout mContentFrameLayout;
    @NonNull
    private final HostCallback mHostCallback;

    private boolean mUseCollapsingToolbar;

    private boolean mIsExpressiveTheme;

    private FloatingToolbarLayout mFloatingToolbarLayout;

    public CollapsingToolbarDelegate(@NonNull HostCallback hostCallback) {
        this(hostCallback, /* useCollapsingToolbar= */ true);
    }

    public CollapsingToolbarDelegate(@NonNull HostCallback hostCallback,
            boolean useCollapsingToolbar) {
        mHostCallback = hostCallback;
        mUseCollapsingToolbar = useCollapsingToolbar;
    }

    /** Method to call that creates the root view of the collapsing toolbar. */
    @SuppressWarnings("RestrictTo")
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return onCreateView(inflater, container, null);
    }

    /** Method to call that creates the root view of the collapsing toolbar. */
    @SuppressWarnings("RestrictTo")
    View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            Activity activity) {
        int layoutId;
        boolean useCollapsingToolbar =
                mUseCollapsingToolbar || Build.VERSION.SDK_INT < Build.VERSION_CODES.S;
        Context context = (activity != null) ? activity : inflater.getContext();
        mIsExpressiveTheme = SettingsThemeHelper.isExpressiveTheme(context);
        if (useCollapsingToolbar) {
            if (mIsExpressiveTheme) {
                if (activity instanceof AppCompatActivity) {
                    layoutId = R.layout.settingslib_expressive_collapsing_toolbar_appcompat_layout;
                } else {
                    layoutId = R.layout.settingslib_expressive_collapsing_toolbar_base_layout;
                }
            } else {
                layoutId = R.layout.collapsing_toolbar_base_layout;
            }
        } else {
            layoutId = R.layout.non_collapsing_toolbar_base_layout;
        }

        final View view = inflater.inflate(layoutId, container, false);
        if (view instanceof CoordinatorLayout) {
            mCoordinatorLayout = (CoordinatorLayout) view;
        }
        mCollapsingToolbarLayout = view.findViewById(R.id.collapsing_toolbar);
        mAppBarLayout = view.findViewById(R.id.app_bar);

        if (!useCollapsingToolbar) {
            // In the non-collapsing toolbar layout, we need to set the background of the app bar to
            // the same as the activity background so that it covers the items extending above the
            // bounds of the list for edge-to-edge.
            TypedArray ta = container.getContext().obtainStyledAttributes(new int[] {
                    android.R.attr.windowBackground});
            Drawable background = ta.getDrawable(0);
            ta.recycle();
            mAppBarLayout.setBackground(background);
        }

        initCollapsingToolbar(mCollapsingToolbarLayout, mAppBarLayout);
        mContentFrameLayout = view.findViewById(R.id.content_frame);

        if (activity instanceof AppCompatActivity) {
            Log.d(TAG, "onCreateView: from AppCompatActivity and sub-class.");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                initSupportActionBar(inflater);
            } else {
                initRSupportActionBar(view);
            }
        } else {
            Log.d(TAG, "onCreateView: from NonAppCompatActivity.");
            mToolbar = view.findViewById(R.id.action_bar);
            final ActionBar actionBar = mHostCallback.setActionBar(mToolbar);
            // Enable title and home button by default
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setHomeButtonEnabled(true);
                if (useCollapsingToolbar && mIsExpressiveTheme) {
                    actionBar.setHomeAsUpIndicator(R.drawable.settingslib_expressive_icon_back);
                }
                actionBar.setDisplayShowTitleEnabled(true);
            }
        }

        initToolbarButtonsContainer(view.findViewById(R.id.toolbar_buttons_container));
        initToolbarPrimaryButton(view.findViewById(R.id.primary_button));
        initToolbarSecondaryButton(view.findViewById(R.id.secondary_button));
        initToolbarActionButton(view.findViewById(R.id.action_button));
        initToolbarActionIconOnlyButton(view.findViewById(R.id.action_icon_only_button));

        initFloatingToolbar(context, view.findViewById(R.id.floating_toolbar));
        return view;
    }

    /**
     * Initialize the collapsing toolbar layout.
     * @param collapsingToolbarLayout
     * @param appBarLayout
     */
    public void initCollapsingToolbar(CollapsingToolbarLayout collapsingToolbarLayout,
            AppBarLayout appBarLayout) {
        if (collapsingToolbarLayout != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            collapsingToolbarLayout.setLineSpacingMultiplier(TOOLBAR_LINE_SPACING_MULTIPLIER);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                collapsingToolbarLayout.setHyphenationFrequency(HYPHENATION_FREQUENCY_NORMAL_FAST);
                collapsingToolbarLayout.setStaticLayoutBuilderConfigurer(builder ->
                        builder.setLineBreakConfig(
                                new LineBreakConfig.Builder()
                                        .setLineBreakWordStyle(
                                                LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                                        .build()));
            }
        }
        setupFrostedCollapsingToolbar(collapsingToolbarLayout, appBarLayout);
        autoSetCollapsingToolbarLayoutScrolling(appBarLayout);
    }

    private void setupFrostedCollapsingToolbar(
            @Nullable CollapsingToolbarLayout collapsingToolbarLayout,
            @Nullable AppBarLayout appBarLayout) {
        if (collapsingToolbarLayout == null || appBarLayout == null) {
            return;
        }

        final Context context = appBarLayout.getContext();
        final int surfaceColor = context.getColor(
                com.android.settingslib.widget.theme.R.color.settingslib_materialColorSurfaceContainerHigh);
        final boolean blurSupported = isBlurSupported(context);
        final int alpha = blurSupported ? 0xCC : 0xEE;
        final int scrimColor = (surfaceColor & 0x00FFFFFF) | (alpha << 24);

        collapsingToolbarLayout.setContentScrim(new ColorDrawable(scrimColor));

        appBarLayout.setWillNotDraw(false);
        appBarLayout.setOutlineProvider(ViewOutlineProvider.BOUNDS);
        appBarLayout.setClipToOutline(true);

        final float blurRadius = 25.0f;

        appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            private boolean mAppliedBlur = false;

            @Override
            public void onOffsetChanged(AppBarLayout appBar, int verticalOffset) {
                final boolean scrolled = Math.abs(verticalOffset) > 0;
                if (scrolled != mAppliedBlur) {
                    mAppliedBlur = scrolled;
                    if (scrolled && isBlurSupported(appBar.getContext())) {
                        applyBackdropBlur(appBar, blurRadius);
                    } else {
                        applyBackdropBlur(appBar, 0);
                    }
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            final WindowManager wm = context.getSystemService(WindowManager.class);
            if (wm != null) {
                final Consumer<Boolean> blurListener = enabled -> {
                    final boolean supported = enabled && isBlurSupported(context);
                    final int newAlpha = supported ? 0xCC : 0xEE;
                    collapsingToolbarLayout.setContentScrim(
                            new ColorDrawable((surfaceColor & 0x00FFFFFF) | (newAlpha << 24)));
                    if (Math.abs(appBarLayout.getTop()) > 0) {
                        applyBackdropBlur(appBarLayout, supported ? blurRadius : 0);
                    }
                };
                appBarLayout.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        try {
                            wm.addCrossWindowBlurEnabledListener(blurListener);
                        } catch (Throwable ignored) {
                        }
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        try {
                            wm.removeCrossWindowBlurEnabledListener(blurListener);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        }
    }

    private static boolean isBlurSupported(Context context) {
        if (!SystemProperties.getBoolean("ro.surface_flinger.supports_background_blur", false)) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WindowManager wm = context.getSystemService(WindowManager.class);
            if (wm != null && !wm.isCrossWindowBlurEnabled()) {
                return false;
            }
        }
        PowerManager pm = context.getSystemService(PowerManager.class);
        if (pm != null && pm.isPowerSaveMode()) {
            return false;
        }
        return true;
    }

    private static void applyBackdropBlur(View view, float radius) {
        try {
            if (radius > 0) {
                RenderEffect effect = RenderEffect.createBlurEffect(
                        radius, radius, Shader.TileMode.CLAMP);
                Method method = View.class.getMethod("setBackdropRenderEffect", RenderEffect.class);
                method.invoke(view, effect);
            } else {
                Method method = View.class.getMethod("setBackdropRenderEffect", RenderEffect.class);
                method.invoke(view, (Object) null);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Initialize toolbar buttons container. */
    public void initToolbarButtonsContainer(View toolbarButtonsContainer) {
        mToolbarButtonsContainer = toolbarButtonsContainer;
    }

    /** Initialize toolbar's primary button. */
    public void initToolbarPrimaryButton(MaterialButton primaryButton) {
        mPrimaryButton = primaryButton;
    }

    /** Initialize toolbar's secondary button. */
    public void initToolbarSecondaryButton(MaterialButton secondaryButton) {
        mSecondaryButton = secondaryButton;
    }

    /** Initialize toolbar's action button. */
    public void initToolbarActionButton(MaterialButton actionButton) {
        mActionButton = actionButton;
    }

    /** Initialize toolbar's action icon only button. */
    public void initToolbarActionIconOnlyButton(MaterialButton actionButtonIconOnly) {
        mActionIconOnlyButton = actionButtonIconOnly;
    }

    /**
     * Initialize the floating toolbar.
     * @param context
     * @param floatingToolbarLayout
     */
    public void initFloatingToolbar(@NonNull Context context,
            @NonNull FloatingToolbarLayout floatingToolbarLayout) {
        mFloatingToolbarLayout = floatingToolbarLayout;
    }

    /** Return an instance of CoordinatorLayout. */
    @Nullable
    public CoordinatorLayout getCoordinatorLayout() {
        return mCoordinatorLayout;
    }

    /** Sets the title on the collapsing layout and delegates to host. */
    public void setTitle(CharSequence title) {
        if (mCollapsingToolbarLayout != null) {
            mCollapsingToolbarLayout.setTitle(title);
        }
        mHostCallback.setOuterTitle(title);
    }

    /** Returns an instance of collapsing toolbar. */
    @Nullable
    public CollapsingToolbarLayout getCollapsingToolbarLayout() {
        return mCollapsingToolbarLayout;
    }

    /** Return the content frame layout. */
    @NonNull
    public FrameLayout getContentFrameLayout() {
        return mContentFrameLayout;
    }

    public Toolbar getToolbar() {
        return mToolbar;
    }

    /** Return an instance of app bar. */
    @Nullable
    public AppBarLayout getAppBarLayout() {
        return mAppBarLayout;
    }

    /**
     * Sets the visibility of the floating toolbar.
     * @param visible
     */
    public void setFloatingToolbarVisibility(boolean visible) {
        if (mFloatingToolbarLayout == null) {
            return;
        }
        mFloatingToolbarLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Sets the toolbar items  for the floating toolbar.
     * @param itemList
     */
    public void setToolbarItems(List<ScrollableToolbarItemLayout.ToolbarItem> itemList) {
        if (mFloatingToolbarLayout == null) {
            return;
        }

        ScrollableToolbarItemLayout layout = mFloatingToolbarLayout.findViewById(
                R.id.floating_toolbar_items);
        if (layout == null) {
            return;
        }

        layout.onItemSelected(itemList);
    }

    private @Nullable ScrollableToolbarItemLayout getToolbarItemsLayout() {
        if (mFloatingToolbarLayout == null) {
            return null;
        }
        return mFloatingToolbarLayout.findViewById(R.id.floating_toolbar_items);
    }

    /**
     * Sets the item selected listener for the floating toolbar.
     */
    public void setOnItemSelectedListener(
            ScrollableToolbarItemLayout.OnItemSelectedListener listener) {
        var layout = getToolbarItemsLayout();
        if (layout != null) {
            layout.setOnItemSelectedListener(listener);
        }
    }

    /**
     * Removes the item selected listener for the floating toolbar.
     */
    public void removeOnItemSelectedListener() {
        var layout = getToolbarItemsLayout();
        if (layout != null) {
            layout.removeOnItemSelectedListener();
        }
    }

    /**
     * Sets the selected toolbar item by its zero-based index.
     */
    public void setSelectedItem(int position) {
        var layout = getToolbarItemsLayout();
        if (layout != null) {
            layout.setSelectedItem(position);
        }
    }

    private void initSupportActionBar(@NonNull LayoutInflater inflater) {
        if (mCollapsingToolbarLayout == null) {
            return;
        }

        if (!SettingsThemeHelper.isExpressiveTheme(inflater.getContext())) {
            mCollapsingToolbarLayout.removeAllViews();
            inflater.inflate(R.layout.support_toolbar, mCollapsingToolbarLayout);
        }

        final androidx.appcompat.widget.Toolbar supportToolbar =
                mCollapsingToolbarLayout.findViewById(R.id.support_action_bar);
        final androidx.appcompat.app.ActionBar actionBar =
                mHostCallback.setActionBar(supportToolbar);
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            if (mIsExpressiveTheme) {
                actionBar.setHomeAsUpIndicator(R.drawable.settingslib_expressive_icon_back);
            }
            actionBar.setDisplayShowTitleEnabled(true);
        }
    }

    private void initRSupportActionBar(View view) {
        view.findViewById(R.id.action_bar).setVisibility(View.GONE);
        final androidx.appcompat.widget.Toolbar supportToolbar =
                view.findViewById(R.id.support_action_bar);
        supportToolbar.setVisibility(View.VISIBLE);
        final androidx.appcompat.app.ActionBar actionBar =
                mHostCallback.setActionBar(supportToolbar);
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            if (mIsExpressiveTheme) {
                actionBar.setHomeAsUpIndicator(R.drawable.settingslib_expressive_icon_back);
            }
            actionBar.setDisplayShowTitleEnabled(true);
        }
    }

    /**
     * Show/Hide the primary button on the Toolbar.
     * @param enabled true to show the button, otherwise it's hidden.
     */
    public void setPrimaryButtonEnabled(boolean enabled) {
        if (mPrimaryButton == null) {
            return;
        }
        int visibility = enabled ? View.VISIBLE : View.GONE;
        mPrimaryButton.setVisibility(visibility);
        showOrHideToolbarButtonsContainer();
    }

    /** Set the icon to the primary button */
    public void setPrimaryButtonIcon(@NonNull Context context, @DrawableRes int drawableRes) {
        if (mPrimaryButton == null) {
            return;
        }
        mPrimaryButton.setIcon(
                context.getResources().getDrawable(drawableRes, context.getTheme()));
    }

    /** Set the OnClick listener to the primary button */
    public void setPrimaryButtonOnClickListener(@Nullable View.OnClickListener listener) {
        if (mPrimaryButton == null) {
            return;
        }
        mPrimaryButton.setOnClickListener(listener);
    }

    /** Set the content description to the primary button */
    public void setPrimaryButtonContentDescription(@Nullable CharSequence contentDescription) {
        if (mPrimaryButton == null) {
            return;
        }
        mPrimaryButton.setContentDescription(contentDescription);
    }

    /**
     * Show/Hide the secondary button on the Toolbar.
     * @param enabled true to show the button, otherwise it's hidden.
     */
    public void setSecondaryButtonEnabled(boolean enabled) {
        if (mSecondaryButton == null) {
            return;
        }
        int visibility = enabled ? View.VISIBLE : View.GONE;
        mSecondaryButton.setVisibility(visibility);
        showOrHideToolbarButtonsContainer();
    }

    /** Set the icon to the secondary button */
    public void setSecondaryButtonIcon(@NonNull Context context, @DrawableRes int drawableRes) {
        if (mSecondaryButton == null) {
            return;
        }
        mSecondaryButton.setIcon(
                context.getResources().getDrawable(drawableRes, context.getTheme()));
    }

    /** Set the OnClick listener to the secondary button */
    public void setSecondaryButtonOnClickListener(@Nullable View.OnClickListener listener) {
        if (mSecondaryButton == null) {
            return;
        }
        mSecondaryButton.setOnClickListener(listener);
    }

    /** Set the content description to the secondary button */
    public void setSecondaryButtonContentDescription(@Nullable CharSequence contentDescription) {
        if (mSecondaryButton == null) {
            return;
        }
        mSecondaryButton.setContentDescription(contentDescription);
    }

    /**
     * Show/Hide the action button on the Toolbar.
     * @param enabled true to show the button, otherwise it's hidden.
     */
    public void setActionButtonEnabled(boolean enabled) {
        if (mActionButton == null || mActionIconOnlyButton == null) {
            return;
        }
        int visibility = enabled ? View.VISIBLE : View.GONE;

        updateActionButton(visibility);
        showOrHideToolbarButtonsContainer();
    }

    /**
     * Enable/Disable the action button on the Toolbar (being clickable or not).
     * @param clickable true to enable the button, otherwise it's disabled.
     */
    public void setActionButtonClickable(boolean clickable) {
        if (mActionButton == null || mActionIconOnlyButton == null) {
            return;
        }
        mActionButton.setEnabled(clickable);
        mActionIconOnlyButton.setEnabled(clickable);
    }

    /** Set the icon to the action button */
    public void setActionButtonIcon(@NonNull Context context, @DrawableRes int drawableRes) {
        if (mActionButton == null || mActionIconOnlyButton == null) {
            return;
        }
        mActionButton.setIcon(context.getResources().getDrawable(drawableRes, context.getTheme()));
        mActionIconOnlyButton.setIcon(
                context.getResources().getDrawable(drawableRes, context.getTheme()));
    }

    /** Set the text to the action button */
    public void setActionButtonText(@Nullable CharSequence text) {
        if (mActionButton == null || mActionIconOnlyButton == null) {
            return;
        }

        boolean isTextNull = text == null;

        if (!isTextNull) {
            mActionButton.setText(text);
        }

        int visibility;
        if (mActionButton.getVisibility() == View.VISIBLE
                || mActionIconOnlyButton.getVisibility() == View.VISIBLE) {
            visibility = View.VISIBLE;
        } else {
            visibility = View.GONE;
        }

        updateActionButton(visibility);
    }

    /** Set the OnClick listener to the action button */
    public void setActionButtonOnClickListener(@Nullable View.OnClickListener listener) {
        if (mActionButton == null || mActionIconOnlyButton == null) {
            return;
        }
        mActionButton.setOnClickListener(listener);
        mActionIconOnlyButton.setOnClickListener(listener);
    }

    /** Set the content description to the action button */
    public void setActionButtonContentDescription(@Nullable CharSequence contentDescription) {
        if (mActionButton == null || mActionIconOnlyButton == null) {
            return;
        }
        mActionButton.setContentDescription(contentDescription);
        mActionIconOnlyButton.setContentDescription(contentDescription);
    }

    /**
     * Set the state of CollapsingToolbar to collapsed when multiple fragments share a single
     * FragmentManager within an activity.
     */
    public void registerToolbarCollapseBehavior(@NonNull Activity activity) {
        if (!(activity instanceof FragmentActivity)) {
            return;
        }
        FragmentManager fragmentManager = ((FragmentActivity) activity).getSupportFragmentManager();
        fragmentManager.registerFragmentLifecycleCallbacks(
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
                        @NonNull View v, @Nullable Bundle savedInstanceState) {
                    super.onFragmentViewCreated(fm, f, v, savedInstanceState);
                    if (!SettingsThemeHelper.isExpressiveTheme(activity)) {
                        return;
                    }
                    // Check if multiple fragments use the same activity
                    if (fm.getBackStackEntryCount() > 0) {
                        AppBarLayout appBarLayout = getAppBarLayout();
                        if (appBarLayout != null) {
                            appBarLayout.post(() -> appBarLayout.setExpanded(false, true));
                        } else {
                            Log.e(TAG, "AppBarLayout is null, can't collapse toolbar.");
                        }
                    }
                }
            }, false);
    }

    private void autoSetCollapsingToolbarLayoutScrolling(AppBarLayout appBarLayout) {
        if (appBarLayout == null) {
            return;
        }
        final CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams();
        final AppBarLayout.Behavior behavior = new IgnoreNonTouchScrollBehavior();
        behavior.setDragCallback(
                new AppBarLayout.Behavior.DragCallback() {
                    @Override
                    public boolean canDrag(@NonNull AppBarLayout appBarLayout) {
                        // Header can be scrolling while device in landscape mode and SDK > 33
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU
                                || SettingsThemeHelper.isExpressiveTheme(
                                appBarLayout.getContext())) {
                            return false;
                        } else {
                            return appBarLayout.getResources().getConfiguration().orientation
                                    == Configuration.ORIENTATION_LANDSCAPE;
                        }
                    }
                });
        params.setBehavior(behavior);
    }

    private void updateActionButton(int visibility) {
        if (mActionButton == null || mActionIconOnlyButton == null) {
            return;
        }

        if (mActionButton.getText().length() == 0) {
            mActionButton.setVisibility(View.GONE);
            mActionIconOnlyButton.setVisibility(visibility);
        } else {
            mActionIconOnlyButton.setVisibility(View.GONE);
            mActionButton.setVisibility(visibility);
        }
    }

    private void showOrHideToolbarButtonsContainer() {
        if (mToolbarButtonsContainer == null) {
            return;
        }

        boolean enabled = false;

        // If at least one button inside toolbar buttons container is visible, make the container
        // visible, otherwise it should be invisible to remove the custom padding it requires
        if (mPrimaryButton != null) {
            enabled |= mPrimaryButton.getVisibility() == View.VISIBLE;
        }

        if (mSecondaryButton != null) {
            enabled |= mSecondaryButton.getVisibility() == View.VISIBLE;
        }

        if (mActionButton != null) {
            enabled |= mActionButton.getVisibility() == View.VISIBLE;
        }

        if (mActionIconOnlyButton != null) {
            enabled |= mActionIconOnlyButton.getVisibility() == View.VISIBLE;
        }

        int visibility = enabled ? View.VISIBLE : View.GONE;
        mToolbarButtonsContainer.setVisibility(visibility);
    }
}
