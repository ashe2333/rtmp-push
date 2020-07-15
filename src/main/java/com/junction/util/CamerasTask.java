package com.junction.util;


import com.junction.cache.CacheUtil;
import com.junction.controller.CameraController;
import com.junction.pojo.CameraPojo;
import com.junction.pojo.ServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;


@Component
@EnableScheduling//可以在启动类上注解也可以在当前文件
public class CamerasTask {

    private final static Logger logger = LoggerFactory.getLogger(TimerUtil.class);

    private static final RestTemplate restTemplate = new RestTemplate();

    private static String token = "";

    private static boolean isEffective =false;

    public static final String TOKEN = "Authentication";

    private final CameraController cameraController;

    CamerasTask(CameraController cameraController){
        this.cameraController =cameraController;
    }




   static  {
        List<HttpMessageConverter<?>> list = restTemplate.getMessageConverters();
        for (HttpMessageConverter<?> httpMessageConverter : list) {
            if (httpMessageConverter instanceof StringHttpMessageConverter){
                ((StringHttpMessageConverter)
                        httpMessageConverter).setDefaultCharset(Charset.forName("utf-8"));
                break;
            }
        }

    }


    private final ClassPathResource classPathResource = new ClassPathResource("auth.txt");

    private void login(){
        try {
            File auth = classPathResource.getFile();
            Map<String,String> loginForm = new HashMap<>();
            BufferedReader bufferedReader = new BufferedReader(new FileReader(auth));
            String line = null;
            line = bufferedReader.readLine();
            loginForm.put("username",line);
            line = bufferedReader.readLine();
            loginForm.put("password",line);
            ServerResponse res = restTemplate.postForObject("http://localhost:7000/user/login",loginForm, ServerResponse.class);
            if(res!=null&&res.success){
                Map<String,String> data = (Map<String,String>)res.data;
                token= data.get("token");
                isEffective = true;
                return;
            }
            logger.error("账号或密码错误");

        }catch (IOException io){
            logger.error("找不到auth认证文件");
        }
        isEffective = false;
    }

    private List<Map<String,Object>> getList(){
        HttpHeaders header = new HttpHeaders();
        header.set(TOKEN,token);
        header.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(header);
       ServerResponse serverResponse= restTemplate.getForObject("http://localhost:7004/api/v1/camera/list",ServerResponse.class);
       if(serverResponse!=null&&serverResponse.success){
           Map map = (Map) serverResponse.data;
           if(map!=null&&map.containsKey("records")){
               return (List) map.get("records");
           }
       }
       isEffective = false;
       logger.error("获取摄像头信息错误");
       return null;
    }



    @Scheduled(cron = "0 */1 * * * ?")
    public void task() throws InterruptedException {
        logger.info("--------------start-------------");
        for (int i = 0; i <3&&!isEffective ; i++) {
            login();
            Thread.sleep(3000);
        }
        if(isEffective){
            List<Map<String,Object>> list = getList();
            Map<String, CameraPojo> cache = CacheUtil.STREAMMAP;
            if(list!=null){
                if(cache==null||cache.size()==0){
                    for (Map<String,Object> map:list) {
                        CameraPojo pojo = new CameraPojo();
                        Integer status =(Integer) map.get("status");
                        if(!status.equals(0)) continue;
                        pojo.setToken(map.get("token").toString());
                        pojo.setIp(map.get("ip").toString());
                        pojo.setRtsp(map.get("source").toString());
                        Map<String, String> data=cameraController.openCamera(pojo);
                        logger.info("推流地址------"+data.get("url"));
                    }
                }else {
                    Set<String> activeKeys = new HashSet<>();
                    for (Map<String,Object> map:list) {
                        CameraPojo pojo = new CameraPojo();
                        Integer status = (Integer) map.get("status");
                        if(status.equals(0)){
                            String token  = map.get("token").toString();
                            if(cache.containsKey(token)) continue;
                            pojo.setToken(token);
                            pojo.setIp(map.get("ip").toString());
                            pojo.setRtsp(map.get("source").toString());
                            Map<String, String> data= cameraController.openCamera(pojo);
                            logger.info("推流地址------"+data.get("url"));
                        }else {
                            String key  = map.get("token").toString();
                            if(cache.containsKey(key)){
                                // 结束线程
                                CameraController.jobMap.get(key).setInterrupted();
                                // 清除缓存
                                CacheUtil.STREAMMAP.remove(key);
                                CameraController.jobMap.remove(key);
                            }
                        }
                        activeKeys.add(map.get("token").toString());
                    }
                    Set<String> allKeys =cache.keySet();
                    for (String key:allKeys){
                            if(!activeKeys.contains(key)) {
                                // 结束线程
                                CameraController.jobMap.get(key).setInterrupted();
                                // 清除缓存
                                CacheUtil.STREAMMAP.remove(key);
                                CameraController.jobMap.remove(key);
                            }

                    }

                }
            }

        }
       logger.info("--------------end-------------");
    }


}
