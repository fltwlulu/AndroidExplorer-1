package com.sjj.echo.explorer;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.NavigationView.OnNavigationItemSelectedListener;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.sjj.echo.explorer.routine.FileTool;

import java.util.ArrayList;
import java.util.List;

import io.github.privacysecurer.core.Event;
import io.github.privacysecurer.core.ImageCallback;
import io.github.privacysecurer.core.ImageEvent;
import io.github.privacysecurer.core.UQI;

//must implements ActivityCompat.OnRequestPermissionsResultCallback or it will crash !!
/**
 * the main activity of the explorer.
 * */
public class MainActivity extends AppCompatActivity
        implements OnNavigationItemSelectedListener,FileFragment.OnListChangeListener,ActivityCompat.OnRequestPermissionsResultCallback
{
    private FloatingActionButton mFabNew;
    private Toolbar mToolbar;
    private ViewPager mViewPager;
    private FloatingActionButton mBtnCancel;
    private FloatingActionButton mBtnPaste;
    private FloatingActionButton mBtnNew;
    private TabLayout mTabLayout;
    private FilePageAdapter mFilePageAdapter;
    private boolean mTabBarAdded = true;
    private boolean mPasteForCopy;
    protected String mClipBaseDir;
    protected List<String> mClipBoard;//save the mName of the mFiles or directories
    private boolean mPasteMode = false;
    protected String mHomeDir = "/";
    private NavigationView mNavigationView;
    private Menu mMenu;
    private TextView mTextCount;
    private Drawable mNavIcon;
    private String mTitle;
    private int mLastCount;
    protected SharedPreferences mSharedPreferences;
    private boolean mCloseSelectAfterAction = true;

    static final String KEY_EXPLORER_HOME_PATH ="KEY_EXPLORER_HOME_PATH";
    static final String KEY_EXPLORER_TAB_COUNT ="KEY_EXPLORER_TAB_COUNT";
    static final String KEY_EXPLORER_TAB_BASE ="KEY_EXPLORER_TAB_BASE";
    static final String KEY_EXPLORER_TAB_SELECT ="KEY_EXPLORER_TAB_SELECT";

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Context context = getApplicationContext();

        UQI uqi = new UQI(this);
        Event imageEvent = new ImageEvent.ImageEventBuilder()
                .setEventType(Event.Image_Content_Updated)
                .build();
        uqi.addEventListener(imageEvent, new ImageCallback() {
            @Override
            public void onEvent() {
                Toast.makeText(context, "Updates! Sync now!", Toast.LENGTH_SHORT).show();
            }
        });

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        mBtnNew = (FloatingActionButton) findViewById(R.id.fab_new);
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        final ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, mToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mBtnNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onNewDialog();
            }
        });
        mBtnCancel = (FloatingActionButton) findViewById(R.id.fab_paste_cancel);
        mBtnPaste = (FloatingActionButton) findViewById(R.id.fab_paste);

        drawer.setDrawerListener(toggle);
        toggle.syncState();
        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);
        mFabNew = (FloatingActionButton) findViewById(R.id.fab_new);
        if(Build.VERSION.SDK_INT>=23)
            getPermission();
        mTabLayout = (TabLayout) findViewById(R.id.tab_layout);
        mViewPager = (ViewPager) findViewById(R.id.id_viewpager);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mHomeDir = mSharedPreferences.getString(KEY_EXPLORER_HOME_PATH,mHomeDir);
        int tabCount = mSharedPreferences.getInt(KEY_EXPLORER_TAB_COUNT,0);
        String[] initDirs = new String[tabCount];
        for(int i=0;i<tabCount;i++)
        {
            initDirs[i] = mSharedPreferences.getString(KEY_EXPLORER_TAB_BASE+i,"/");
        }
        mFilePageAdapter = new FilePageAdapter(getSupportFragmentManager(),this, mViewPager,initDirs);
        mViewPager.setAdapter(mFilePageAdapter);
        mTabLayout.setupWithViewPager(mViewPager);
        mViewPager.setCurrentItem(mSharedPreferences.getInt(KEY_EXPLORER_TAB_SELECT,0));
        autoShowBar();

        mBtnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitPaste();
            }
        });
        mBtnPaste.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPaste();
            }
        });

    }

    //to copy or move the items in the mClipBoard determined by mPasteForCopy
    void onPaste()
    {
        String curPath = getCurFileList().getCurPath();
        for(String name : mClipBoard)
        {
            if(mPasteForCopy)
                FileTool.copy(mClipBaseDir +name,curPath);
            else
                FileTool.move(mClipBaseDir +name,curPath);
        }
        exitPaste();
        getCurFragment().refresh(false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean permissionGet = true;
        for(int grant:grantResults)
        {
            if(grant == PackageManager.PERMISSION_DENIED)
                permissionGet = false;
        }
        if(permissionGet == false)
        {
            new AlertDialog.Builder(this).setTitle("请求权限").setMessage("请在 \"设置\"-\"权限\" 中允许相关授权")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            MainActivity.this.getPermission();
                        }
                    }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    MainActivity.this.finish();
                }
            }).create().show();
        }
        else//directory maybe opened before the permission approved,so refresh them.
        {
            int count = mFilePageAdapter.getCount();
            for(int i=0;i<count;i++)
            {
                FileFragment fileFragment = (FileFragment) mFilePageAdapter.getItem(i);
                fileFragment.refresh();
            }
            //getCurFragment().refresh();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void getPermission()
    {
        String[] permissions = {"android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.WRITE_EXTERNAL_STORAGE"};
        ArrayList<String> preToDo = new ArrayList<>();
        boolean tip = false;
        for(String pre:permissions)
        {
            if(checkSelfPermission(pre)!=PackageManager.PERMISSION_GRANTED)
            {
                preToDo.add(pre);
                if(shouldShowRequestPermissionRationale(pre)) {
                    tip = true;
                }
            }
        }
        if(preToDo.size()==0)
            return;
        if (tip)
            Toast.makeText(this,"please approve the authorization for file manager",Toast.LENGTH_LONG).show();
        requestPermissions(preToDo.toArray(new String[preToDo.size()]),0);

    }

    //call this method to hide the TabLayout when the ViewPager is not switching.
    protected void autoShowBar()
    {
        final AppBarLayout appBarLayout = (AppBarLayout) findViewById(R.id.app_bar);
        appBarLayout.removeView(mTabLayout);
        mTabBarAdded = false;
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {

            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if(state==ViewPager.SCROLL_STATE_DRAGGING&&!mTabBarAdded) {
                    appBarLayout.addView(mTabLayout);
                    mTabBarAdded = true;
                }

                if(state==ViewPager.SCROLL_STATE_IDLE&& mTabBarAdded){
                    appBarLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        appBarLayout.removeView(mTabLayout);
                        mTabBarAdded = false;
                    }
                    },1000);
                }
                if(state==ViewPager.SCROLL_STATE_SETTLING)
                {
                    FileFragment fileFragment = ((FileFragment)((FilePageAdapter) mViewPager.getAdapter())
                            .getItem(mViewPager.getCurrentItem()));
                    FileListView fileListView = fileFragment.mFileList;
                    if(fileListView!=null)
                        mToolbar.setTitle(fileListView.getCurPath());
                    else
                        mToolbar.setTitle(fileFragment.mLaunchDir);
                }
            }
        });
    }
    /**
     * open the directory in current tab.
     * <p></p>
     * @param path the path of the directory to open, normally it end with "/".
     * */
    public void openDir(String path)
    {
        ((FileFragment)((FilePageAdapter) mViewPager.getAdapter()).getItem(mViewPager.getCurrentItem()))
                .mFileList.openDirAuto(path,true);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the mMenu; this adds items to the action bar if it is present.
        this.mMenu = menu;
        return false;
    }

    /**
     * return the current fragment.
     * */
    public FileFragment getCurFragment()
    {
        return (FileFragment) mFilePageAdapter.getItem(mViewPager.getCurrentItem());
    }

    /**
     * return the current file list.
     * */
    public FileListView getCurFileList()
    {
        return getCurFragment().mFileList;
    }
    /**
     * return the current file list.
     * */
    public FileAdapter getCurFileAdapter()
    {
        return (FileAdapter) getCurFileList().getAdapter();
    }
    /**
     * return the the directory witch show int the foreground.
     * */
    public String getCurPath()
    {
        return getCurFileList().getCurPath();
    }

    //call this method to enter paste mode,fat button will be restored, mClipBaseDir and mClipBoard will be saved.
    private void enterPaste()
    {
        mPasteMode = true;
        mBtnNew.setVisibility(View.INVISIBLE);
        mBtnCancel.setVisibility(View.VISIBLE);
        mBtnPaste.setVisibility(View.VISIBLE);
        String curString = getCurPath();
        mClipBaseDir = new String(curString);
        List<String> selets = getCurFileAdapter().getSelect();
        mClipBoard = new ArrayList<>(selets);
    }

    //save the directory path witch opened to restored them next mTime
    @Override
    protected void onPause() {
        super.onPause();
        int count = mFilePageAdapter.getCount();
        mSharedPreferences.edit().putInt(KEY_EXPLORER_TAB_COUNT,count).commit();
        for(int i=0;i<count;i++)
        {
            FileFragment fileFragment = (FileFragment)mFilePageAdapter.getItem(i);
            FileListView fileListView = fileFragment.mFileList;
            String _path;
            if(fileListView!=null)
                _path = fileListView.getCurPath();
            else
                _path = fileFragment.mLaunchDir;
            mSharedPreferences.edit().putString(KEY_EXPLORER_TAB_BASE+i,_path).commit();
        }
        mSharedPreferences.edit().putInt(KEY_EXPLORER_TAB_SELECT,mViewPager.getCurrentItem()).commit();
    }

    //call this method to exit paste mode,fat button will be changed
    private void exitPaste()
    {
        mPasteMode = false;
        mBtnNew.setVisibility(View.VISIBLE);
        mBtnCancel.setVisibility(View.INVISIBLE);
        mBtnPaste.setVisibility(View.INVISIBLE);

    }

    //setup the menu actions
    private boolean onMenu(int id)
    {
        if (id == R.id.action_close) {
            mFilePageAdapter.removeTab(mViewPager.getCurrentItem());
            mToolbar.setTitle(getCurPath());
            return true;
        }else if(id == R.id.action_refresh)
        {
            getCurFragment().refresh();
            return true;
        }else if(id == R.id.action_search)
        {
            return true;
        }else if(id == R.id.action_view)
        {
            return true;
        }else if(id == R.id.action_sort)
        {
            String[] sortModes = {"名称","日期","大小","类型","名称(降序)","日期(降序)","大小(降序)"};
            new AlertDialog.Builder(MainActivity.this).setTitle("选择排序模式")
                    .setSingleChoiceItems(sortModes,0, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FileListView fileListView = getCurFileList();
                            switch (which)
                            {
                                case 0:
                                    fileListView.mSortBy = FileListView.SortBy.SORT_NAME;
                                    fileListView.mFileSortRising = true;
                                    break;
                                case 1:
                                    fileListView.mSortBy = FileListView.SortBy.SORT_TIME;
                                    fileListView.mFileSortRising = true;
                                    break;
                                case 2:
                                    fileListView.mSortBy = FileListView.SortBy.SORT_SIZE;
                                    fileListView.mFileSortRising = true;
                                    break;
                                case 3:
                                    fileListView.mSortBy = FileListView.SortBy.SORT_TYPE;
                                    break;
                                case 4:
                                    fileListView.mSortBy = FileListView.SortBy.SORT_NAME;
                                    fileListView.mFileSortRising = false;
                                    break;
                                case 5:
                                    fileListView.mSortBy = FileListView.SortBy.SORT_TIME;
                                    fileListView.mFileSortRising = false;
                                    break;
                                case 6:
                                    fileListView.mSortBy = FileListView.SortBy.SORT_SIZE;
                                    fileListView.mFileSortRising = false;
                                    break;
                            }
                            getCurFragment().refresh();
                            dialog.cancel();
                        }
                    }).create().show();
            return true;
        }else if(id == R.id.action_set_as_home)
        {
            mHomeDir = getCurPath();
            mSharedPreferences.edit().putString(KEY_EXPLORER_HOME_PATH,mHomeDir).commit();
            return true;
        }
        else if(id == R.id.select_menu_back)
        {

            return true;
        }else if(id == R.id.select_menu_copy)
        {
            mPasteForCopy = true;
            enterPaste();
            return true;
        }else if(id == R.id.select_menu_cut)
        {
            mPasteForCopy = false;
            enterPaste();
            return true;
        }else if(id == R.id.select_menu_delete)
        {
            final List<String> selects = getCurFileAdapter().getSelect();
            final int count  = selects.size();
            String title;
            if(count == 1)
                title = selects.get(0);
            else
                title = "多个项目";
            new AlertDialog.Builder(MainActivity.this).setTitle(title).setMessage("确定删除所选项目?")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String curPath = getCurFileList().getCurPath();
                            for(int i=0;i<count;i++)
                            {
                                FileTool.deleteFile(curPath+selects.get(i));
                            }
                            getCurFragment().refresh(false);
                        }
                    }).setNegativeButton("取消",null).create().show();
            return true;
        }else if(id == R.id.select_menu_rename)
        {
            final View view = MainActivity.this.getLayoutInflater().inflate(R.layout.new_file_dir_layout,null);
            final List<String> selects = getCurFileAdapter().getSelect();
            final String curPath = getCurFileList().getCurPath();
            final String oldName = selects.get(0);
            final EditText editText = (EditText)view.findViewById(R.id.new_file_name);
            editText.setText(oldName);
            new AlertDialog.Builder(MainActivity.this).setTitle("文件夹").setView(view)
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            String name = editText.getText().toString();
                            FileTool.reName(curPath+oldName,curPath+name);
                            getCurFragment().refresh(false);
                        }
                    }).setNegativeButton("取消",null).create().show();
            return true;
        }else if(id == R.id.select_menu_select_all)
        {
            mCloseSelectAfterAction = false;
            getCurFileList().selectAll();
            return true;
        }else if(id == R.id.select_menu_send)
        {
            Intent intent = null;
            List<String> selects = getCurFileAdapter().getSelect();
            String curPath = getCurPath();
            int selectNum = selects.size();
            if(1==selectNum)
            {
                intent = new Intent(Intent.ACTION_SEND);
                intent.setType("*/*");//intent.setType("application/*");
                intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://"+curPath+selects.get(0)));
            }
            else {
                intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                intent.setType("*/*");
                ArrayList<Uri> sendFiles = new ArrayList<Uri>();
                for(int i=0;i<selectNum;i++)
                {
                    sendFiles.add(Uri.parse("file://"+curPath+selects.get(i)));
                }
                intent.putExtra(Intent.EXTRA_STREAM, sendFiles);
            }
            startActivity(intent);
            //getCurFragment().mFileList.exitSelect();
            return true;
        }
        return false;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent mActivity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        boolean ret = onMenu(id);
        //by default,it will exit the select mode when a action mSelected,to keep the select mode, set mCloseSelectAfterAction true
        if(mCloseSelectAfterAction)
            getCurFileList().exitSelect();
        else
            mCloseSelectAfterAction = true;
       if(ret)
           return true;
        return super.onOptionsItemSelected(item);
    }

    //show a dialog to ask what to create.
    private void onNewDialog()
    {

        View view = getLayoutInflater().inflate(R.layout.dlg_new,null);
        final AlertDialog alertDialog = new AlertDialog.Builder(this).setTitle("新建.....").setView(view).create();
        view.findViewById(R.id.new_dlg_btn_dir).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final View view = MainActivity.this.getLayoutInflater().inflate(R.layout.new_file_dir_layout,null);
                new AlertDialog.Builder(MainActivity.this).setTitle("文件夹").setView(view)
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String name = ((EditText)view.findViewById(R.id.new_file_name)).getText().toString();
                                FileTool.newDir(getCurFileList().getCurPath()+name);
                                getCurFragment().refresh(false);
                            }
                        }).setNegativeButton("取消",null).create().show();
                alertDialog.cancel();
            }
        });

        view.findViewById(R.id.new_dlg_btn_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final View view = MainActivity.this.getLayoutInflater().inflate(R.layout.new_file_dir_layout,null);
                new AlertDialog.Builder(MainActivity.this).setTitle("文件").setView(view)
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String name = ((EditText)view.findViewById(R.id.new_file_name)).getText().toString();
                                FileTool.newFile(getCurFileList().getCurPath()+name);
                                getCurFragment().refresh(false);
                            }
                        }).setNegativeButton("取消",null).create().show();
                alertDialog.cancel();
            }
        });

        view.findViewById(R.id.new_dlg_btn_root_path).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFilePageAdapter.addTab("/");
                alertDialog.cancel();
            }
        });
        view.findViewById(R.id.new_dlg_btn_internal_sdcard).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String path = Environment.getExternalStorageDirectory().getPath();
                if(path!=null&&path.length()>0)
                    mFilePageAdapter.addTab(path);
                else
                    Toast.makeText(MainActivity.this,"can't detect the internal sdcard",Toast.LENGTH_SHORT).show();
                alertDialog.cancel();
            }
        });
        view.findViewById(R.id.new_dlg_btn_extend_sdcard).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String path = FileTool.getStoragePath(MainActivity.this,true);
                if(path!=null)
                {
                    mFilePageAdapter.addTab(path);
                }
                else
                    Toast.makeText(MainActivity.this,"can't detect the extend sdcard",Toast.LENGTH_SHORT).show();
                alertDialog.cancel();
            }
        });
        alertDialog.show();
    }

    //set up the navigation actions
    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_exit) {
            this.finish();
            // Handle the camera action
        } else if (id == R.id.nav_favorite) {

        } else if (id == R.id.nav_home) {
            openDir(mHomeDir);
        } else if (id == R.id.nav_info) {

            new AlertDialog.Builder(MainActivity.this).setView(MainActivity.this.getLayoutInflater().inflate(R.layout.layout_info,null)).setPositiveButton("确定",null).create().show();

        } else if (id == R.id.nav_new) {
            onNewDialog();
        } else if (id == R.id.nav_search) {

        }
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    //to enter the select mode,it will save and clear the title,save and replace the navigation icon,replace the menu
    private void onEnterSelect()
    {
        if(mMenu !=null) {
            mMenu.clear();
            getMenuInflater().inflate(R.menu.main_select, mMenu);
        }
        mTextCount = (TextView) android.support.v4.view.MenuItemCompat.getActionView(mMenu.findItem(R.id.select_menu_count));
        if(mToolbar !=null) {
            mNavIcon = mToolbar.getNavigationIcon();
            mToolbar.setNavigationIcon(null);
            mTitle = (String) mToolbar.getTitle();
            mToolbar.setTitle("");
        }
    }

    //to exit the select mode,it will restore the title, navigation icon,and the menu.
    void onExitSelect()
    {
        if(mMenu !=null) {
            mMenu.clear();
            getMenuInflater().inflate(R.menu.main, mMenu);
        }
        if(mToolbar !=null) {
            mToolbar.setNavigationIcon(mNavIcon);
            mToolbar.setTitle(mTitle);
        }
        mLastCount = 0;
    }

    //the listener called when one or more items are mSelected or unselected.
    // according the select count, it may enter or exit the select mode and update the select count show in the toolbar.
    @Override
    public void onSelectChange(FileFragment fileFragment, int selectCount) {

        if(mLastCount == 0&&selectCount == 1)
        {
            if(mPasteMode)
                exitPaste();
            onEnterSelect();
        }
        if(mLastCount > 0&&selectCount == 0)
        {
            onExitSelect();
        }
        if(selectCount == 2|| selectCount == 1)
        {
            //when multi item select some menu item should be unavailable ,just remove them
            if(mMenu !=null) {
                int[] ids = {R.id.select_menu_rename,R.id.select_menu_set_premission,R.id.select_menu_property,
                        R.id.select_menu_open_as_text,R.id.select_menu_set_owner,R.id.select_menu_home_shortcut,R.id.select_menu_link};
                int count = ids.length;
                for (int i = 0; i < count; i++) {
                    MenuItem menuItem = mMenu.findItem(ids[i]);
                    //MenuItem MenuItem = mMenu.getItem(ids[i]);
                    if (selectCount == 1) {
                        menuItem.setVisible(true);
                    } else {
                        menuItem.setVisible(false);
                    }
                }
            }
        }

        if(mTextCount !=null) {
            mTextCount.setText("" + selectCount);
        }
        mLastCount = selectCount;
    }
    //called when open a directory or restore a directory from the the back queue.
    @Override
    public void onTitleChange(final FileFragment fileFragment, final String title) {
        if(mTabLayout!=null) {
            int pos = mFilePageAdapter.getItemPosition(fileFragment);
            if(mToolbar!=null) {
                int curPos = mTabLayout.getSelectedTabPosition();
                if(pos == curPos)
                    mToolbar.setTitle(title);
            }
            /*note that new tab may not have been added to mTabLayout yet,just wait for a while*/
            if(pos >= mTabLayout.getTabCount())
                mTabLayout.post(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.this.onTitleChange(fileFragment,title);
                    }
                });
            else
                mTabLayout.getTabAt(pos).setText(FileTool.pathToName(title));
        }

    }
}
