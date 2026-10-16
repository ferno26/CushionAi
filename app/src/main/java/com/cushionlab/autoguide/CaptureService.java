package com.cushionlab.autoguide;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import java.nio.ByteBuffer;

/** One non-touchable Android overlay window, plus local MediaProjection frame processing. */
public final class CaptureService extends Service {
    public static final String ACTION_STOP="com.cushionlab.autoguide.STOP";
    private static final String CHANNEL="cushion_capture";
    private final Object sessionLock=new Object();
    private final Handler mainHandler=new Handler(android.os.Looper.getMainLooper());
    private HandlerThread captureThread;
    private Handler captureHandler;
    private WindowManager wm;
    private WindowManager.LayoutParams overlayParams;
    private GuideView guide;
    private MediaProjection projection;
    private MediaProjection.Callback projectionCallback;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private SharedPreferences prefs;
    private int captureWidth,captureHeight;
    private long lastFrame;
    private volatile boolean stopping;

    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(projection!=null)return START_NOT_STICKY;
        if(intent==null||!Settings.canDrawOverlays(this)) {stopSelf();return START_NOT_STICKY;}
        Intent captureData;
        if(Build.VERSION.SDK_INT>=33)captureData=intent.getParcelableExtra("captureData",Intent.class);
        else captureData=intent.getParcelableExtra("captureData");
        if(captureData==null){stopSelf();return START_NOT_STICKY;}
        startNotification();
        prefs=getSharedPreferences("auto_guide",MODE_PRIVATE);
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        captureThread=new HandlerThread("local-frame-detector");captureThread.start();
        captureHandler=new Handler(captureThread.getLooper());
        try{
            MediaProjectionManager mgr=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=mgr.getMediaProjection(Activity.RESULT_OK,captureData);
            if(projection==null)throw new IllegalStateException("No MediaProjection");
            projectionCallback=new MediaProjection.Callback(){
                @Override public void onStop(){mainHandler.post(()->stopSelf());}
                @Override public void onCapturedContentResize(int width,int height){
                    mainHandler.post(()->{
                        if(!stopping && virtualDisplay!=null && width>0 && height>0 &&
                                (width!=captureWidth||height!=captureHeight)) resizeProjection(width,height);
                    });
                }
            };
            projection.registerCallback(projectionCallback,mainHandler);
            createOverlay();
            Point size=screenSize();
            createCapture(size.x,size.y);
        }catch(Exception e){android.util.Log.e("CushionAutoGuide","Capture startup failed",e);stopSelf();}
        return START_NOT_STICKY;
    }
    private void startNotification(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Cushion Lab capture",NotificationManager.IMPORTANCE_LOW));
        Intent stop=new Intent(this,CaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopPi=PendingIntent.getService(this,10,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent openPi=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Cushion Lab — screen guidance active")
                .setContentText("Local capture and bounce prediction. Tap to adjust.")
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null,"Stop",stopPi).build())
                .build();
        startForeground(202,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }
    private Point screenSize(){
        Point p=new Point();wm.getDefaultDisplay().getRealSize(p);
        if(p.x<1)p.x=1080;if(p.y<1)p.y=2400;
        return p;
    }
    private void createOverlay(){
        guide=new GuideView(this,prefs);
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                |WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                |WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                |WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        overlayParams=new WindowManager.LayoutParams(-1,-1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.TRANSLUCENT);
        overlayParams.gravity=Gravity.TOP|Gravity.START;
        if(Build.VERSION.SDK_INT>=28)overlayParams.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        overlayParams.alpha=Math.max(.1f,Math.min(.7f,prefs.getInt("opacity",45)/100f));
        wm.addView(guide,overlayParams);
    }
    private void createCapture(int screenW,int screenH){
        captureWidth=screenW;captureHeight=screenH;
        float scale=Math.min(1f,960f/Math.max(screenW,screenH));
        int w=Math.max(200,(int)(screenW*scale));int h=Math.max(120,(int)(screenH*scale));
        imageReader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
        installFrameListener(imageReader);
        if(virtualDisplay==null){
            int density=getResources().getDisplayMetrics().densityDpi;
            virtualDisplay=projection.createVirtualDisplay("Cushion Lab local capture",w,h,density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,imageReader.getSurface(),null,captureHandler);
        }else{
            virtualDisplay.resize(w,h,getResources().getDisplayMetrics().densityDpi);
            virtualDisplay.setSurface(imageReader.getSurface());
        }
    }
    private void installFrameListener(ImageReader readerInstance){
        readerInstance.setOnImageAvailableListener(reader->{
            Image im=null;
            try{
                im=reader.acquireLatestImage();
                if(im==null||stopping)return;
                long now=android.os.SystemClock.uptimeMillis();
                if(now-lastFrame<260)return;
                lastFrame=now;
                Image.Plane plane=im.getPlanes()[0];
                ByteBuffer buf=plane.getBuffer();
                int row=plane.getRowStride(),pixel=plane.getPixelStride();
                int width=im.getWidth(),height=im.getHeight();
                int[] rgba=new int[width*height];
                for(int y=0;y<height;y++){
                    int off=y*row;
                    for(int x=0;x<width;x++){
                        int at=off+x*pixel;
                        int red=buf.get(at)&255,green=buf.get(at+1)&255,blue=buf.get(at+2)&255;
                        rgba[y*width+x]=0xff000000|(red<<16)|(green<<8)|blue;
                    }
                }
                FrameAnalyzer.Scene scene=FrameAnalyzer.analyze(rgba,width,height);
                final int fw=width,fh=height;
                mainHandler.post(()->{if(guide!=null){guide.submit(scene,fw,fh);updateOverlayOpacity();}
                    adjustCaptureSize();});
            }catch(Exception ex){android.util.Log.w("CushionAutoGuide","Capture frame failed",ex);}
            finally{if(im!=null)im.close();}
        },captureHandler);
    }
    private void adjustCaptureSize(){
        if(stopping||virtualDisplay==null||imageReader==null)return;
        Point p=screenSize();
        if(p.x!=captureWidth||p.y!=captureHeight)resizeProjection(p.x,p.y);
    }
    private void resizeProjection(int width,int height){
        if(stopping||virtualDisplay==null||imageReader==null)return;
        final ImageReader old=imageReader;
        imageReader=null;
        old.setOnImageAvailableListener(null,null);
        // Surface switch must happen before old ImageReader is closed.
        float scale=Math.min(1f,960f/Math.max(width,height));
        int w=Math.max(200,(int)(width*scale));int h=Math.max(120,(int)(height*scale));
        ImageReader next=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
        virtualDisplay.resize(w,h,getResources().getDisplayMetrics().densityDpi);
        virtualDisplay.setSurface(next.getSurface());
        old.close();
        // Reuse createCapture's listener setup without re-creating a VirtualDisplay.
        imageReader=next;
        captureWidth=width;captureHeight=height;
        installFrameListener(next);
    }
    private void updateOverlayOpacity(){
        if(overlayParams==null||guide==null)return;
        float a=Math.max(.1f,Math.min(.7f,prefs.getInt("opacity",45)/100f));
        if(Math.abs(a-overlayParams.alpha)<.005f)return;
        overlayParams.alpha=a;
        try{wm.updateViewLayout(guide,overlayParams);}catch(RuntimeException ignored){}
    }
    @Override public void onDestroy(){
        stopping=true;
        // Disable callback first so our voluntary release does not trigger a second stop.
        if(projection!=null&&projectionCallback!=null)try{projection.unregisterCallback(projectionCallback);}catch(Exception ignored){}
        if(virtualDisplay!=null){virtualDisplay.release();virtualDisplay=null;}
        if(imageReader!=null){imageReader.close();imageReader=null;}
        if(projection!=null){projection.stop();projection=null;}
        if(guide!=null){try{wm.removeView(guide);}catch(Exception ignored){}guide=null;}
        if(captureThread!=null)captureThread.quitSafely();
        super.onDestroy();
    }
}
