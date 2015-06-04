package io.ucoin.app.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;

import io.ucoin.app.Application;
import io.ucoin.app.R;
import io.ucoin.app.config.Configuration;
import io.ucoin.app.database.Contract;
import io.ucoin.app.database.Provider;
import io.ucoin.app.fragment.common.HomeFragment;
import io.ucoin.app.fragment.currency.CurrencyListFragment;
import io.ucoin.app.fragment.wallet.TransferFragment;
import io.ucoin.app.fragment.web.WebFragment;
import io.ucoin.app.fragment.wot.WotSearchFragment;
import io.ucoin.app.model.remote.Identity;
import io.ucoin.app.service.ServiceLocator;
import io.ucoin.app.service.exception.PeerConnectionException;
import io.ucoin.app.service.remote.WotRemoteService;
import io.ucoin.app.technical.CurrencyUtils;
import io.ucoin.app.technical.DateUtils;
import io.ucoin.app.technical.ExceptionUtils;
import io.ucoin.app.technical.exception.UncaughtExceptionHandler;
import io.ucoin.app.technical.task.AsyncTaskHandleException;


public class MainActivity extends ActionBarActivity
        implements ListView.OnItemClickListener,
        IToolbarActivity,
        LoaderManager.LoaderCallbacks<Cursor>,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int MIN_SEARCH_CHARACTERS = 2;
    private ActionBarDrawerToggle mToggle;
    private DrawerLayout mDrawerLayout;
    private QueryResultListener<Identity> mQueryResultListener;

    private TextView mUidView;
    private TextView mPubkeyView;
    private Toolbar mToolbar;
    private boolean mUnitPreferenceChanged = false;

    private ServiceLocator mServiceLocator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prepare some utilities
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler(this));
        DateUtils.setDefaultMediumDateFormat(getMediumDateFormat());
        DateUtils.setDefaultLongDateFormat(getLongDateFormat());
        DateUtils.setDefaultShortDateFormat(getShortDateFormat());
        DateUtils.setDefaultTimeFormat(getTimeFormat());
        CurrencyUtils.setDefaultLocale(getResources().getConfiguration().locale);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);

        // Init configuration
        Configuration config = new Configuration();
        Configuration.setInstance(config);

        // Load account
        AccountManager accountManager = AccountManager.get(this);
        Account[] accounts = accountManager.getAccountsByType(getString(R.string.ACCOUNT_TYPE));

        // If first time: create account
        if (accounts.length == 0) {
            Intent intent = new Intent(this, AddAccountActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        //todo handle this case
        Account account = loadLastAccountUsed(accountManager, accounts);
        if (account == null) {
            Toast.makeText(this, "Could Not load account", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);

        try {
            setSupportActionBar(mToolbar);
        } catch (Throwable t) {
            Log.w("setSupportActionBar", t.getMessage());
        }

        //Navigation drawer
        final View listHeader = getLayoutInflater().inflate(R.layout.drawer_header, null);
        listHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onItemClick(null, listHeader, 1, 0); // go to home
            }
        });
        mUidView = (TextView) listHeader.findViewById(R.id.uid);
        mPubkeyView = (TextView) listHeader.findViewById(R.id.public_key);

        String[] drawerListItems = getResources().getStringArray(R.array.drawer_items);
        ListView drawerListView = (ListView) findViewById(R.id.drawer_listview);

        drawerListView.addHeaderView(listHeader);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        // Set the adapter for the drawer list view
        drawerListView.setAdapter(new ArrayAdapter<>(this,
                R.layout.drawer_list_item, drawerListItems));

        drawerListView.setOnItemClickListener(this);
        //Navigation drawer toggle
        //Please use ActionBarDrawerToggle(Activity, DrawerLayout, int, int)
        // if you are setting the Toolbar as the ActionBar of your activity.
        mToggle = new ActionBarDrawerToggle(this, mDrawerLayout
                , R.string.open_drawer, R.string.close_drawer);


        ContentResolver.setSyncAutomatically(account, getString(R.string.AUTHORITY), true);

        // Open the default fragment
        openDefaultFragment();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mToggle.syncState();
    }

    /**
     * This method will detect when a change on pref should restart the main activity
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(SettingsActivity.PREF_UNIT)) {
            mUnitPreferenceChanged = true;
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mUnitPreferenceChanged) {
            openHomeFragment();
            mUnitPreferenceChanged = false;
        }
    }

    @Override
    protected void onDestroy() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }


    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        //todo handle screen orientation change
        //for now it is just discarded by adding
        //android:configChanges="orientation|screenSize" in the manifest
        super.onConfigurationChanged(newConfig);
        mToggle.onConfigurationChanged(newConfig);
    }

    //Called once during the whole activity lifecycle
    // after the first onResume() call
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mToggle.onOptionsItemSelected(item))
            return true;

        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(Gravity.START)) {
            mDrawerLayout.closeDrawer(Gravity.START);
            return;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();

        int bsEntryCount = fragmentManager.getBackStackEntryCount();
        if (bsEntryCount <= 1) {
            super.onBackPressed();
            return;
        }

        String currentFragment = fragmentManager
                .getBackStackEntryAt(bsEntryCount - 1)
                .getName();

        Fragment fragment = fragmentManager.findFragmentByTag(currentFragment);

        //fragment that need to handle onBackPressed
        //shoud implements MainActivity.OnBackPressedInterface
        if(fragment instanceof OnBackPressed) {
            if(((OnBackPressed) fragment).onBackPressed()) {
                return;
            }
        }

        fragmentManager.popBackStack();
    }

    public boolean onQueryTextSubmit(MenuItem searchItem, String query) {

        searchItem.getActionView().clearFocus();
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.frame_content);
        boolean isWotFragmentExists = fragment == mQueryResultListener;

        // If fragment already visible, just refresh the arguments (to update title)
        if (!isWotFragmentExists) {
            fragment = WotSearchFragment.newInstance(query);
            mQueryResultListener = (WotSearchFragment)fragment;
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(
                            R.animator.delayed_fade_in,
                            R.animator.fade_out,
                            R.animator.delayed_fade_in,
                            R.animator.fade_out)
                    .replace(R.id.frame_content, fragment, fragment.getClass().getSimpleName())
                    .addToBackStack(fragment.getClass().getSimpleName())
                    .commit();
        }
        else {
            WotSearchFragment.setArguments((WotSearchFragment) fragment, query);
        }

        if (query.length() >= MIN_SEARCH_CHARACTERS) {
            SearchTask searchTask = new SearchTask();
            searchTask.execute(query);
        }
        else {
            mQueryResultListener.onQueryFailed(getString(R.string.query_too_short, MIN_SEARCH_CHARACTERS));
        }

        return true;
    }

    // nav drawer items
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Fragment fragment = null;
        switch (position) {
            case 1: //0 is home we only pop back, no need for new fragment
                break;
            case 2:
                fragment = CurrencyListFragment.newInstance();
                break;
            case 3:
                fragment = WebFragment.newInstance();
                break;
            case 4:
                Intent intent = new Intent(MainActivity.this,
                        SettingsActivity.class);
                startActivity(intent);
                break;
            default:

        }

        //replace fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragment == null) {
            fragmentManager.popBackStack(HomeFragment.class.getSimpleName(), 0);
        } else {
            // Insert the fragment by replacing any existing fragment
            fragmentManager.popBackStack(HomeFragment.class.getSimpleName(), 0);
            fragmentManager.beginTransaction()
                    .setCustomAnimations(
                            R.animator.delayed_fade_in,
                            R.animator.fade_out,
                            R.animator.delayed_fade_in,
                            R.animator.fade_out)
                    .replace(R.id.frame_content, fragment, fragment.getClass().getSimpleName())
                    .addToBackStack(fragment.getClass().getSimpleName())
                    .commit();
        }

        // close the drawer
        mDrawerLayout.closeDrawer(findViewById(R.id.drawer_listview));
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Create the data loader, using cursor
        long accountId = ((Application) getApplication()).getAccountId();
        Uri uri = Uri.parse(Provider.CONTENT_URI + "/account/" + accountId);

        return new CursorLoader(this, uri, null,
                null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data == null) {
            return;
        }
        int uidIndex = data.getColumnIndex(Contract.Account.UID);
        int pubkeyIndex = data.getColumnIndex(Contract.Account.PUBLIC_KEY);

        while (data.moveToNext()) {
            mUidView.setText(data.getString(uidIndex));
            mPubkeyView.setText(data.getString(pubkeyIndex));
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        Log.d("MAINACTIVITY", "onLoaderReset");
    }

    public void setToolbarColor(int colorRes) {
        mToolbar.setBackgroundColor(colorRes);
    }

    public void setToolbarDrawable(Drawable drawable) {
        mToolbar.setBackground(drawable);
    }

    /**
     * Display an an arrow in the toolbar to get to the previous fragment
     * or an hamburger icon to open the navigation drawer
     */
    @Override
    public void setToolbarBackButtonEnabled(boolean enabled) {
        if (enabled) {
            mToggle.setDrawerIndicatorEnabled(false);
            getSupportActionBar().setHomeButtonEnabled(true);
        } else {
            mToggle.setDrawerIndicatorEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(false);
        }

    }

    /* -- Internal methods -- */

    protected DateFormat getMediumDateFormat() {
        final String format = Settings.System.getString(getContentResolver(), Settings.System.DATE_FORMAT);
        if (TextUtils.isEmpty(format)) {
            return android.text.format.DateFormat.getMediumDateFormat(getApplicationContext());
        } else {
            return new SimpleDateFormat(format);
        }
    }

    protected DateFormat getLongDateFormat() {
        return android.text.format.DateFormat.getLongDateFormat(getApplicationContext());
    }

    protected DateFormat getShortDateFormat() {
        return android.text.format.DateFormat.getDateFormat(getApplicationContext());
    }

    protected DateFormat getTimeFormat() {
        return android.text.format.DateFormat.getTimeFormat(getApplicationContext());
    }

    protected void openDefaultFragment() {
        Intent intent = getIntent();

        // Open transfer if a given URI exists
        if (intent != null && intent.getAction() == Intent.ACTION_VIEW) {
            Uri uri = intent.getData();
            Log.d("MAINACTIVITY", "Asking to open uri: " + uri.toString());

            Identity identity = new Identity();
            List<String> pathSegments = uri.getPathSegments();

            if (pathSegments.size()== 2) {
                identity.setCurrency(pathSegments.get(0));
                identity.setPubkey(pathSegments.get(1));

                openTransferFragment(identity);
                return;
            }
        }

        // Open the home screen
        openHomeFragment();
    }

    protected void openHomeFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();

        Fragment fragment = fragmentManager.findFragmentById(R.id.frame_content);
        if (fragment != null && fragment instanceof HomeFragment) {
            fragmentManager.beginTransaction()
                    .setCustomAnimations(
                            R.animator.fade_in,
                            R.animator.fade_out)
                    .remove(fragment)
                    .commit();
            fragmentManager.popBackStack();
        }

        fragment = HomeFragment.newInstance();

        fragmentManager.beginTransaction()
                .setCustomAnimations(
                        R.animator.fade_in,
                        R.animator.fade_out)
                .add(R.id.frame_content, fragment, fragment.getClass().getSimpleName())
                .addToBackStack(fragment.getClass().getSimpleName())
                .commit();

        // Init app (caches) in background thread
        new InitTask().execute();
    }



    protected void openTransferFragment(Identity identity) {
        FragmentManager fragmentManager = getSupportFragmentManager();

        Fragment fragment = fragmentManager.findFragmentById(R.id.frame_content);
        if (fragment != null && fragment instanceof TransferFragment) {
            fragmentManager.beginTransaction().remove(fragment).commit();
            fragmentManager.popBackStack();
        }

        fragment = TransferFragment.newInstance(identity);

        fragmentManager.beginTransaction()
                .setCustomAnimations(
                        R.animator.fade_in,
                        R.animator.fade_out)
                .add(R.id.frame_content, fragment, fragment.getClass().getSimpleName())
                .addToBackStack(fragment.getClass().getSimpleName())
                .commit();
    }

    protected Account loadLastAccountUsed(AccountManager accountManager, Account[] accounts) {

        for (Account account : accounts) {
            String account_id = accountManager.getUserData(account, "_id");

            String last_account_id = getSharedPreferences("account", MODE_PRIVATE)
                    .getString("_id", "");

            if (last_account_id.equals(account_id)) {
                // Init the account to use, and init the data loader,
                ((Application) getApplication()).setAccount(account);
                this.getLoaderManager().initLoader(0, null, this);
                return account;
            }
        }

        return null;
    }


    /**
     * Interface for handling OnBackPressed event in fragments     *
     */
    public interface OnBackPressed {
        /**
         *
         * @return true if the events has been handled, false otherwise
         */
        public boolean onBackPressed();
    }


    public interface QueryResultListener<T> {
        public void onQuerySuccess(List<? extends T> identities);

        public void onQueryFailed(String message);

        public void onQueryCancelled();
    }

    /**
     * Initialize the app (load caches)
     */
    public class InitTask extends AsyncTaskHandleException<Void, Void, Void> {

        private final long mAccountId;

        public InitTask() {
            super(MainActivity.this.getApplicationContext());
            mAccountId = ((io.ucoin.app.Application)getApplication()).getAccountId();
        }

        @Override
        protected Void doInBackgroundHandleException(Void... params) throws Exception {
            ServiceLocator.instance().loadCaches(getContext(), mAccountId);
            return null;
        }
    }

    public class SearchTask extends AsyncTaskHandleException<String, Void, List<Identity>> {

        public SearchTask() {
            super(MainActivity.this);
        }

        @Override
        protected List<Identity> doInBackgroundHandleException(String... queries) throws PeerConnectionException {

            // Get list of currencies
            Set<Long> currenciesIds = ServiceLocator.instance().getCurrencyService().getCurrencyIds();

            WotRemoteService service = ServiceLocator.instance().getWotRemoteService();
            List<Identity> results = service.findIdentities(currenciesIds, queries[0]);

            if (results == null) {
                return null;
            }

            return results;
        }

        @Override
        protected void onSuccess(List<Identity> identities) {
            mQueryResultListener.onQuerySuccess(identities);
        }

        @Override
        protected void onFailed(Throwable t) {
            mQueryResultListener.onQueryFailed(ExceptionUtils.getMessage(t));
        }

        @Override
        protected void onCancelled() {
            mQueryResultListener.onQueryCancelled();
        }
    }


}