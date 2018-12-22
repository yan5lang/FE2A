package com.szetn.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import com.szetn.model.Album;
import com.szetn.model.PhotoItem;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileUtils {

    private static String BASE_PATH;
    private static String STICKER_BASE_PATH;
    public static String FIBERROOT="/fibereye/";
    private static String T_FLASH_PATH = "/storage/sdcard1";

    private static FileUtils mInstance;

    public static FileUtils getInst() {
        if (mInstance == null) {
            synchronized (FileUtils.class) {
                if (mInstance == null) {
                    mInstance = new FileUtils();
                }
            }
        }
        return mInstance;
    }

    /**
     * 获取临时目录
     */
    public File getStorageDir() {
        File dir;
        if (isTFlashCardExists()) {
            dir = new File(T_FLASH_PATH, getMainDirName());
        } else {
            dir = new File(Environment.getExternalStorageDirectory(), getMainDirName());
        }

        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private boolean isTFlashCardExists() {
        boolean tfExistsFlag = false;
        tfExistsFlag = new File(T_FLASH_PATH, "Android").exists();

        if (getStorageDirWhenInsertSdcard() != null && testNewTfFile() == true) {
            tfExistsFlag = true;
        }
        return tfExistsFlag;
    }
    private File getStorageDirWhenInsertSdcard() {
        File dir;
        try {
            dir = new File(T_FLASH_PATH, getMainDirName());
        } catch (Exception e) {
            return null;
        }

        if (!dir.exists()) {
            dir.mkdirs();
        }
//        log.debug("dir:{}, free:{}", dir.getAbsolutePath(), dir.getFreeSpace());

        return dir;
    }

    private boolean testNewTfFile() {
        File testFile = new File(T_FLASH_PATH, "testNewFile");
        boolean returnFlag = false;
        if (!testFile.exists()) {
            try {
                if (testFile.createNewFile()) {
                    returnFlag = true;
                    testFile.delete();
                }
            } catch (IOException e) {
                returnFlag = false;
            }
        } else {
            testFile.delete();
            returnFlag = true;
        }
        return returnFlag;
    }

    private String getMainDirName() {
        return FIBERROOT;
    }

    public File getExtFile(String path) {
        return new File(BASE_PATH + path);
    }

    /**
     * 获取文件夹大小
     * @param file File实例
     * @return long 单位为K
     * @throws Exception
     */
    public long getFolderSize(File file) {
        try {
            long size = 0;
            if (!file.exists()) {
                return size;
            } else if (!file.isDirectory()) {
                return file.length() / 1024;
            }
            File[] fileList = file.listFiles();
            for (int i = 0; i < fileList.length; i++) {
                if (fileList[i].isDirectory()) {
                    size = size + getFolderSize(fileList[i]);
                } else {
                    size = size + fileList[i].length();
                }
            }
            return size / 1024;
        } catch (Exception e) {
            return 0;
        }
    }

    public String getUriSize(String uri){
        long size=0;
        try {
            File f = new File(uri.replace("file://",""));
            if(f.exists())
                size = f.length()/1024;
        } catch (Exception e){
        }
        return size+" Kb";
    }

    public String getBasePath(int packageId) {
        return STICKER_BASE_PATH + packageId + "/";
    }

    private String getImageFilePath(int packageId, String imageUrl) {
        String md5Str = MD5Util.getMD5(imageUrl).replace("-", "mm");
        return getBasePath(packageId) + md5Str;
    }

    public void removeAddonFolder(int packageId) {
        String filename = getBasePath(packageId);
        File file = new File(filename);
        if (file.exists()) {
            delete(file);
        }
    }

    public void delete(File file) {
        if (file.isFile()) {
            file.delete();
            return;
        }

        if (file.isDirectory()) {
            File[] childFiles = file.listFiles();
            if (childFiles == null || childFiles.length == 0) {
                file.delete();
                return;
            }

            for (int i = 0; i < childFiles.length; i++) {
                delete(childFiles[i]);
            }
            file.delete();
        }
    }

    //
    //	获取照片录像保存路径
    public static String getPhoteFullName(){
        return FileUtils.getInst().getPhotoSavedPath()+getTimesString()+".jpg";
    }
    //	获取时间
    private static String getTimesString(){
        SimpleDateFormat dateFormat24 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SS");
        String time = dateFormat24.format(Calendar.getInstance().getTime());
        time = time.replace(" ", "_");

        return time.replace(":", "-");
    }

    public String getPhotoSavedPath() {
        return BASE_PATH + FIBERROOT;
    }

    public String getPhotoTempPath() {
        return BASE_PATH + "stickercamera";
    }

    public String getSystemPhotoPath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera";
    }


    private FileUtils() {
       /* String sdcardState = Environment.getExternalStorageState();
        //如果没SD卡则放缓存
        if (Environment.MEDIA_MOUNTED.equals(sdcardState)) {
            BASE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()+File.separator;
//                        + "/stickercamera/";
        } else {
            BASE_PATH = App.getApp().getCacheDirPath();
        }*/

        if (isTFlashCardExists()) {
            BASE_PATH = T_FLASH_PATH;
        } else {
            BASE_PATH = Environment.getExternalStorageDirectory().getPath();
        }
        STICKER_BASE_PATH = BASE_PATH;// + "/stickers/";
    }

    public boolean createFile(File file) {
        try {
            if (!file.getParentFile().exists()) {
                mkdir(file.getParentFile());
            }
            return file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean mkdir(File file) {
        while (!file.getParentFile().exists()) {
            mkdir(file.getParentFile());
        }
        return file.mkdir();
    }

    public boolean writeSimpleString(File file, String string) {
        FileOutputStream fOut = null;
        try {
            fOut = new FileOutputStream(file);
            fOut.write(string.getBytes());
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        } finally {
            IOUtil.closeStream(fOut);
        }
    }

    public String readSimpleString(File file) {
        StringBuffer sb = new StringBuffer();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));

            String line = br.readLine();
            if (StringUtils.isNotEmpty(line)) {
                sb.append(line.trim());
                line = br.readLine();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            return "";
        } finally {
            IOUtil.closeStream(br);
        }
        return sb.toString();
    }

    //都是相对路径，一一对应
    public boolean copyAssetDirToFiles(Context context, String dirname) {
        try {
            AssetManager assetManager = context.getAssets();
            String[] children = assetManager.list(dirname);
            for (String child : children) {
                child = dirname + '/' + child;
                String[] grandChildren = assetManager.list(child);
                if (0 == grandChildren.length)
                    copyAssetFileToFiles(context, child);
                else
                    copyAssetDirToFiles(context, child);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    //都是相对路径，一一对应
    public boolean copyAssetFileToFiles(Context context, String filename) {
        return copyAssetFileToFiles(context, filename, getExtFile("/" + filename));
    }

    private boolean copyAssetFileToFiles(Context context, String filename, File of) {
        InputStream is = null;
        FileOutputStream os = null;
        try {
            is = context.getAssets().open(filename);
            createFile(of);
            os = new FileOutputStream(of);

            int readedBytes;
            byte[] buf = new byte[1024];
            while ((readedBytes = is.read(buf)) > 0) {
                os.write(buf, 0, readedBytes);
            }
            os.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            IOUtil.closeStream(is);
            IOUtil.closeStream(os);
        }
    }

    public boolean renameDir(String oldDir, String newDir) {
        File of = new File(oldDir);
        File nf = new File(newDir);
        return of.exists() && !nf.exists() && of.renameTo(nf);
    }

    /**  
     * 复制单个文件  
     */
    public void copyFile(String oldPath, String newPath) {
        InputStream inStream = null;
        FileOutputStream fs = null;
        try {
            int bytesum = 0;
            int byteread = 0;
            File oldfile = new File(oldPath);
            if (oldfile.exists()) { //文件存在时   
                inStream = new FileInputStream(oldPath); //读入原文件
                fs = new FileOutputStream(newPath);
                byte[] buffer = new byte[1444];
                while ((byteread = inStream.read(buffer)) != -1) {
                    bytesum += byteread; //字节数 文件大小   
                    System.out.println(bytesum);
                    fs.write(buffer, 0, byteread);
                }
            }
        } catch (Exception e) {
            System.out.println("复制单个文件操作出错");
            e.printStackTrace();
        } finally {
            IOUtil.closeStream(inStream);
            IOUtil.closeStream(fs);
        }

    }


    public ArrayList<PhotoItem> findPicsInDir(String path) {
        ArrayList<PhotoItem> photos = new ArrayList<PhotoItem>();
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            for (File file : dir.listFiles(new FileFilter() {

                @Override
                public boolean accept(File pathname) {
                    String filePath = pathname.getAbsolutePath();
                    return (filePath.endsWith(".png") || filePath.endsWith(".jpg") || filePath
                            .endsWith(".jepg"));
                }
            })) {
                photos.add(new PhotoItem(file.getAbsolutePath(), file.lastModified()));
            }
        }
        Collections.sort(photos);
        return photos;
    }

    public String[] getStorageUriList(){
        String[] urils, oriImgs;
        StringBuffer sb = new StringBuffer("");

        File parentFolder = getStorageDir();
        oriImgs = parentFolder.list();
        urils = new String[oriImgs.length];
        for (int i=oriImgs.length-1,j=0 ; i>=0; i--,j++ ) {
            sb.append("file://").append(parentFolder.getAbsolutePath().toString()).append(File.separator).append(oriImgs[i]);
            urils[j] = sb.toString();
            sb.setLength(0);
        }
        return urils;
    }

    public void getStorageUriList(ArrayList<String> paths){
        if (paths == null)
            paths = new ArrayList<String>();
        else paths.clear();

        StringBuffer sb = new StringBuffer("");
        File parentFolder = getStorageDir();
        String[] oriImgs = parentFolder.list();
        for(int i=oriImgs.length-1,j=0; i>=0; i--,j++) {
            sb.append("file://").append(parentFolder.getAbsolutePath().toString()).append(File.separator).append(oriImgs[i]);
//            sb.append(parentFolder.getAbsolutePath().toString()).append(File.separator).append(oriImgs[i]);
            paths.add(sb.toString());
            sb.setLength(0);
        }
    }


}
