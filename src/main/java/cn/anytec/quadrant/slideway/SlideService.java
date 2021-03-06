package cn.anytec.quadrant.slideway;

import cn.anytec.config.GeneralConfig;
import cn.anytec.quadrant.expZone.ExpDataCallBack;
import cn.anytec.quadrant.hcEntity.DeviceInfo;
import cn.anytec.quadrant.hcService.HCSDKHandler;
import com.sun.jna.NativeLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class SlideService {

    private static final Logger logger = LoggerFactory.getLogger(SlideService.class);
    private static String slideId;
    private volatile static int reFresh = 0;
    private ThreadLocal<String> slideId_threadLocal = new ThreadLocal();
    private ThreadLocal<Integer> reFresh_threadLocal = new ThreadLocal();
    private static Thread pre_thread;

    private DeviceInfo prepareView;
    private DeviceInfo gateView;
    private DeviceInfo closeView;
    private DeviceInfo farView;
    private volatile Boolean endFlag = false;
    private volatile Boolean glissadeFlag = false;
    private volatile short glissadeMode = 0;

    @Autowired
    HCSDKHandler hcsdkHandler;
    @Autowired
    GeneralConfig config;




    public void notifySlide(){
        if(slideId == null){
            logger.info("slideID为空，不进行视频录制");
            return;
        }
        slideId_threadLocal.set(slideId);
        reFresh_threadLocal.set(reFresh);

        //slideId = null;
        try {
            if (!hcsdkHandler.loginCamera(farView)) {
                logger.error("远景摄像头注册失败");
                return;
            }
            if (!hcsdkHandler.loginCamera(closeView)) {
                logger.error("近景摄像头注册失败");
                return;
            }
            String contextPath = new StringBuilder(config.getVideoContext())
                    .append(File.separator).append(slideId_threadLocal.get()).toString();
            File visitorContext = new File(contextPath);
            if(!visitorContext.exists()){
                if(!visitorContext.mkdir()){
                    logger.error("创建游客文件夹失败");
                    return;
                }
            }
            logger.info("开启远景摄像头预览:"+farView.getDeviceIp());
            SlideDataCallBack farCallBack = new SlideDataCallBack(new File(visitorContext,"far.tmp"));
            NativeLong lRealPlayHandle_far = hcsdkHandler.preView(farView,farCallBack);
            Thread.sleep(config.getDelay());
            logger.info("开启近景摄像头预览："+closeView.getDeviceIp());
            SlideDataCallBack closeCallBack = new SlideDataCallBack(new File(visitorContext,"close.tmp"));
            NativeLong lRealPlayHandle_close = hcsdkHandler.preView(closeView,closeCallBack);
            Thread.sleep(config.getDuration0());
            hcsdkHandler.stopPreView(lRealPlayHandle_close);
            logger.info("关闭近景摄像头预览");
            closeCallBack.close();
            closeCallBack.rename();
            Thread.sleep(config.getDuration1() - config.getDelay() - config.getDuration0());
            hcsdkHandler.stopPreView(lRealPlayHandle_far);
            logger.info("关闭远景摄像头预览");
            logger.info("视频流写入完毕："+slideId_threadLocal.get());
            Thread.sleep(config.getXuanma_ready());
            if(reFresh_threadLocal.get() == reFresh){
                if(!glissadeFlag){
                    logger.info("触发关闭滑梯口预览");
                    glissadeFlag = true;
                }
                farCallBack.close();
                farCallBack.rename();
            }else {
                farCallBack.close();
            }
            if(slideId_threadLocal.get().equals(slideId))
                slideId = null;


        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void startPrepareCamera(String visitorId) {
        while (pre_thread != null && pre_thread.isAlive()){
            pre_thread.interrupt();
        }
        pre_thread = new Thread(()->{
            try {
                if (!hcsdkHandler.loginCamera(prepareView)) {
                    logger.error("预备摄像头注册失败");
                    return;
                }
                if (!hcsdkHandler.loginCamera(gateView)) {
                    logger.error("滑梯口摄像头注册失败");
                    return;
                }
                String contextPath = new StringBuilder(config.getVideoContext())
                        .append(File.separator).append(visitorId).toString();
                File visitorContext = new File(contextPath);
                if (!visitorContext.exists()) {
                    if (!visitorContext.mkdir()) {
                        logger.error("创建游客文件夹失败");
                        return;
                    }
                }
                logger.info("开启预备摄像头预览:" + prepareView.getDeviceIp());
                SlideDataCallBack preCallBack = new SlideDataCallBack(new File(visitorContext, "pre.tmp"));
                NativeLong lRealPlayHandle_pre = hcsdkHandler.preView(prepareView, preCallBack);
                setEndFlag(false);
                for(int i=0;i<=config.getPrepareDuration()/1000;i++){
                    if(i == config.getPrepareDuration()/1000){
                        logger.info("超过刷卡后的最大等待时间！");
                        slideId = null;
                        return;
                    }
                    Thread.sleep(1000);
                    if(endFlag){
                        break;
                    }
                }
                logger.info("关闭预备摄像头预览:" + prepareView.getDeviceIp());
                hcsdkHandler.stopPreView(lRealPlayHandle_pre);
                preCallBack.close();
                preCallBack.rename();
                //滑梯口摄像头录制
                logger.info("开启滑梯口摄像头预览:" + gateView.getDeviceIp());
                SlideDataCallBack gateCallBack = new SlideDataCallBack(new File(visitorContext, "gate.tmp"));
                NativeLong lRealPlayHandle_gate = hcsdkHandler.preView(gateView, gateCallBack);
                double farDuration = config.getDuration1()/1000.0;
                double io_module_delayDuration = config.getIo_module_delayTime()/1000.0;
                double xuanma_ready = config.getXuanma_ready()/1000.0;
                double viewDuration = farDuration+io_module_delayDuration+xuanma_ready+config.getGateMax();
                for(int i=0;i < viewDuration+1;i++){
                    if(glissadeFlag){
                        glissadeMode = 1;
                        break;
                    }
                    Thread.sleep(1000);
                }
                logger.info("关闭滑梯口摄像头预览:" + gateView.getDeviceIp());
                hcsdkHandler.stopPreView(lRealPlayHandle_gate);
                gateCallBack.close();
                gateCallBack.rename();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        pre_thread.setDaemon(true);
        pre_thread.start();
    }

    public void setPrepareView(DeviceInfo prepareView) {
        this.prepareView = prepareView;
    }
    public void setGateView(DeviceInfo gateView) {
        this.gateView = gateView;
    }
    public void setFarView(DeviceInfo farView) {
        this.farView = farView;
    }
    public void setCloseView(DeviceInfo closeView) {
        this.closeView = closeView;
    }
    public void setSlideId(String slideId) {
        SlideService.slideId = slideId;
    }
    public String getSlideId() {
        return slideId;
    }
    public void removeId(String id){
        if(slideId != null && id.equals(slideId))
            slideId = null;
    }

    public void setEndFlag(boolean endFlag) {
        this.endFlag=endFlag;
    }

    public void reFreshSlideSignal() {
        reFresh++;
    }

    public void setReFreshGlissadeFlag(){
        glissadeFlag = false;
    }
    public void resetGlissadeMode(){
        glissadeMode = 0;
    }
    public int getGlissadeMode(){
        return glissadeMode;
    }

}
