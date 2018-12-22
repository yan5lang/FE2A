package com.szetn.fibereye;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiskCache;
import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;
import com.nostra13.universalimageloader.cache.memory.impl.LruMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;
import com.nostra13.universalimageloader.core.display.RoundedBitmapDisplayer;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;
import com.nostra13.universalimageloader.utils.StorageUtils;
import com.szetn.customview.swipemenulist.BaseSwipListAdapter;
import com.szetn.customview.swipemenulist.MySwipeRefreshLayout;
import com.szetn.customview.swipemenulist.SwipeMenu;
import com.szetn.customview.swipemenulist.SwipeMenuCreator;
import com.szetn.customview.swipemenulist.SwipeMenuItem;
import com.szetn.customview.swipemenulist.SwipeMenuListView;
import com.szetn.util.AppConstants;
import com.szetn.util.FileUtils;
import com.szetn.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by yan5l on 11/21/2016.
 */
public class FilesFragment extends BaseFragment {

    private ArrayList<String> mFilePaths = new ArrayList<String>();
    private List<String> selectid = new ArrayList<String>();
    private boolean isMulChoice = false; //是否多选
    private FileAdapter mFileAdapter;

    View rootView;
    private MySwipeRefreshLayout swipeLayout; private boolean isRefresh = false;//是否刷新中
    private SwipeMenuListView mListView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        initImageLoader();

        rootView = inflater.inflate(R.layout.files_layout, container, false);
        swipeLayout = (MySwipeRefreshLayout) rootView.findViewById(R.id.swipe_container);
        swipeLayout.setOnRefreshListener(new MySwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                swipeLayout.setRefreshing(false);
                refresh();
                mFileAdapter.notifyDataSetChanged();
                isRefresh= false;
            }
        });
        swipeLayout.setTouchSlop(60);
        mListView = (SwipeMenuListView)rootView.findViewById(android.R.id.list);
//        registerForContextMenu(mListView);

        try {
            FileUtils.getInst().getStorageUriList(mFilePaths);
        } catch (RuntimeException e) {
//            e.printStackTrace();
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getActivity().getPackageName(), null);
            intent.setData(uri);
            getActivity().startActivity(intent);
            Toast.makeText(getActivity(), R.string.info_open_authority, Toast.LENGTH_LONG).show();
        }

        mFileAdapter = new FileAdapter(getActivity());
        mListView.setAdapter(mFileAdapter);
        installRelatedEvent();
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   //应用运行时，保持屏幕高亮，不锁屏

        return rootView;
    }

    private void installRelatedEvent() {

        // step 1. create a MenuCreator
        SwipeMenuCreator creator = new SwipeMenuCreator() {

            @Override
            public void create(SwipeMenu menu) {
                // create "open" item
                SwipeMenuItem openItem = new SwipeMenuItem(getActivity());
                // set item background
                openItem.setBackground(new ColorDrawable(Color.rgb(0xC9, 0xC9,
                        0xCE)));
                // set item width
                openItem.setWidth(dp2px(70));
                // set item title
                openItem.setTitle("Open");
                // set item title fontsize
                openItem.setTitleSize(18);
                // set item title font color
                openItem.setTitleColor(Color.WHITE);
                // add to menu
                menu.addMenuItem(openItem);

                // create "delete" item
                SwipeMenuItem deleteItem = new SwipeMenuItem(getActivity());
                // set item background
                deleteItem.setBackground(new ColorDrawable(Color.rgb(0xF9,
                        0x3F, 0x25)));
                // set item width
                deleteItem.setWidth(dp2px(70));
                // set a icon
                deleteItem.setIcon(R.drawable.del_icon_normal);
                // add to menu
                menu.addMenuItem(deleteItem);
            }
        };
        // set creator
        mListView.setMenuCreator(creator);

        // step 2. listener item click event
        mListView.setOnMenuItemClickListener(new SwipeMenuListView.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(int position, SwipeMenu menu, int index) {
                String item = mFilePaths.get(position);
                switch (index) {
                    case 0:
                        // open
                        open(item);
                        break;
                    case 1:
                        // delete
					    delete(item);
                        mFilePaths.remove(position);
                        mFileAdapter.notifyDataSetChanged();
                        break;
                }
                return false;
            }
        });
        // set SwipeListener
        mListView.setOnSwipeListener(new SwipeMenuListView.OnSwipeListener() {

            @Override
            public void onSwipeStart(int position) {
                // swipe start
            }

            @Override
            public void onSwipeEnd(int position) {
                // swipe end
            }
        });

/*
        // set MenuStateChangeListener
        mListView.setOnMenuStateChangeListener(new SwipeMenuListView.OnMenuStateChangeListener() {
            @Override
            public void onMenuOpen(int position) {
            }

            @Override
            public void onMenuClose(int position) {
            }
        });*/

        // other setting
//		listView.setCloseInterpolator(new BounceInterpolator());

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(isCurTag(1))
                    open(mFilePaths.get(position));
            }
        });
       /* mListView.setLongClickable(true);
        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                return false;
            }
        });*/
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AnimateFirstDisplayListener.displayedImages.clear();
        //when click power button, call here force to quit app, add by cg
        //when open the video records, the live page will open the files, strange.
        try {
            mListView.removeAllViews();
        } catch (Exception e){}
    }

    private void delete(String uri) {
        // delete app
        try {
            FileUtils.getInst().delete(new File(uri.replace("file://","")));
        } catch (Exception e) {
        }
    }

    //need to do: to fix the small bug, when open the video and the video didn't finish return, then bug show
    //当视频没有播放完成就退出，程序会自动重启，这时就会出现live界面没有双击响应
    private void open(String uri) {
        Intent intent = new Intent("android.intent.action.VIEW");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri realUri;
        //通过FileProvider创建一个content类型的Uri
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1)
            realUri = FileProvider.getUriForFile(getContext(), "com.szetn.fibereye.fileprovider",
                new File(uri.replace("file://","")));
        else
            realUri = Uri.parse(uri);
        if(uri.endsWith("jpg")||uri.endsWith("png")){
//            intent.setDataAndType(realUri, "image*//*");
            intent.setDataAndType(realUri, "image/*");
        }
        else if(uri.endsWith("avi")||uri.endsWith("mp4"))
            intent.setDataAndType(realUri, "video/*");
//        startActivity(intent);
        getActivity().startActivityForResult(intent, 1);
    }

    private void refresh(){
        try {
            FileUtils.getInst().getStorageUriList(mFilePaths);
        } catch (RuntimeException e) {
//            e.printStackTrace();
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getActivity().getPackageName(), null);
            intent.setData(uri);
            getActivity().startActivity(intent);
            Toast.makeText(getActivity(), R.string.info_open_authority, Toast.LENGTH_LONG).show();
        }
    }

    private int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    /* 每次长按ContextMenu被绑定的View的子控件，都会调用此方法, 这个方法只是弹出右键效果
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        System.out.println("onCreateContextMenu------>");
        getActivity().getMenuInflater().inflate(R.menu.files_menu, menu);
        super.onCreateContextMenu(menu, v, menuInfo);
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch(item.getItemId()) {
            case R.id.add:
                // add stuff here
                return true;
            case R.id.edit:
                // edit stuff here
                return true;
            case R.id.delete:
                // remove stuff here
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }*/

    DisplayImageOptions defaultOptions;
    ImageLoaderConfiguration config;
    private void initImageLoader() {
        if(defaultOptions == null)
            defaultOptions = new DisplayImageOptions.Builder()
                .cacheInMemory(false)
                .imageScaleType(ImageScaleType.EXACTLY)
                .cacheOnDisk(true)
                .build();

        if(config == null){
            config = new ImageLoaderConfiguration.Builder(getActivity())
                    .threadPriority(Thread.NORM_PRIORITY - 2)
                    .defaultDisplayImageOptions(defaultOptions)
                    .denyCacheImageMultipleSizesInMemory()
                    .diskCacheFileNameGenerator(new Md5FileNameGenerator())
                    .diskCache(new UnlimitedDiskCache(StorageUtils.getOwnCacheDirectory(getActivity(), AppConstants.APP_DIR)))
                    .diskCacheSize(100 * 1024 * 1024).tasksProcessingOrder(QueueProcessingType.LIFO)
                    .memoryCache(new LruMemoryCache(2 * 1024 * 1024))
                    .memoryCacheSize(2 * 1024 * 1024)
                    .threadPoolSize(3)
                    .build();
            ImageLoader.getInstance().init(config);
        }
    }

    class FileAdapter extends BaseSwipListAdapter {

        private LayoutInflater inflater;
        private ImageLoadingListener animateFirstListener = new AnimateFirstDisplayListener();
        private DisplayImageOptions options;

        FileAdapter(Context context) {
            inflater = LayoutInflater.from(context);

            options = new DisplayImageOptions.Builder()
                    .showImageOnLoading(R.drawable.ic_stub)
                    .showImageForEmptyUri(R.drawable.ic_empty)
                    .showImageOnFail(R.drawable.ic_error)
                    .cacheInMemory(true)
                    .cacheOnDisk(true)
                    .considerExifParams(true)
                    .displayer(new RoundedBitmapDisplayer(10))
//					.displayer(new CircleBitmapDisplayer(Color.WHITE, 5))
                    .build();

            //多选初始化
        }

        @Override
        public int getCount() {
            return mFilePaths.size();
        }

        @Override
        public String getItem(int position) {
            return mFilePaths.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            final ViewHolder holder;
            if (convertView == null) {
                view = inflater.inflate(R.layout.item_list_image, parent, false);
                holder = new ViewHolder();
                holder.text = (TextView) view.findViewById(R.id.text);
                holder.image = (ImageView) view.findViewById(R.id.image);
                holder.uri = (TextView) view.findViewById(R.id.uri);
                holder.size = (TextView) view.findViewById(R.id.size);
                holder.cb = (CheckBox) view.findViewById(R.id.check);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

//			System.out.println(IMAGE_URLS[position]);
            holder.text.setText(StringUtils.substring(getItem(position),getItem(position).lastIndexOf(File.separator)+1));
            holder.uri.setText(getItem(position));
            holder.size.setText( FileUtils.getInst().getUriSize(getItem(position)) );
            try{
                ImageLoader.getInstance().displayImage(getItem(position), holder.image, options, animateFirstListener);
            } catch (Exception e){}

            return view;
        }
    }

    class ViewHolder {
            CheckBox cb;
            TextView text;
            ImageView image;
            TextView uri;
            TextView size;

        }


    private static class AnimateFirstDisplayListener extends SimpleImageLoadingListener {

        static final List<String> displayedImages = Collections.synchronizedList(new LinkedList<String>());

        @Override
        public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
            if (loadedImage != null) {
                ImageView imageView = (ImageView) view;
                boolean firstDisplay = !displayedImages.contains(imageUri);
                if (firstDisplay) {
                    FadeInBitmapDisplayer.animate(imageView, 500);
                    displayedImages.add(imageUri);
                }
            }
        }
    }
}
