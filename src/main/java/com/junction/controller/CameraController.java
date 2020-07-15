package com.junction.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.junction.cache.CacheUtil;
import com.junction.pojo.CameraPojo;
import com.junction.pojo.Config;
import com.junction.thread.CameraThread;
import com.junction.util.IpUtil;

/**
 * @Title CameraController.java
 * @description controller
 * @time 2019年12月16日 上午9:00:27
 * @author wuguodong
 **/

@RestController
public class CameraController {

	private final static Logger logger = LoggerFactory.getLogger(CameraController.class);

	@Autowired
	public Config config;// 配置文件bean

	// 存放任务 线程
	public static Map<String, CameraThread.MyRunnable> jobMap = new HashMap<String, CameraThread.MyRunnable>();

	/**
	 * @Title: openCamera
	 * @Description: 开启视频流
	 * @return Map<String,String>
	 **/
	@RequestMapping(value = "/cameras", method = RequestMethod.POST)
	public Map<String, String> openCamera(@RequestBody  CameraPojo pojo) {
		// 返回结果
		Map<String, String> map = new HashMap<String, String>();
		String token =pojo.getToken();
			CameraPojo cameraPojo = new CameraPojo();
			// 获取当前时间
			String openTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date().getTime());
			Set<String> keys = CacheUtil.STREAMMAP.keySet();
			// 缓存是否为空
			if (0 == keys.size()) {
				// 开始推流
				cameraPojo = openStream(pojo.getIp(),token,pojo.getRtsp(),openTime);
				map.put("token", cameraPojo.getToken());
				map.put("url", cameraPojo.getUrl());
				logger.info("打开：" + cameraPojo.getRtsp());
			} else {
				// 是否存在的标志；0：不存在；1：存在
				int sign = 0;
					if(keys.contains(token)){
						cameraPojo = CacheUtil.STREAMMAP.get(token);
						sign = 1;
					}
					if (sign == 1) {// 存在
						cameraPojo.setCount( 1);
						cameraPojo.setOpenTime(openTime);
						map.put("token", cameraPojo.getToken());
						map.put("url", cameraPojo.getUrl());
						logger.info("打开：" + cameraPojo.getRtsp());
					} else {
						cameraPojo = openStream(pojo.getIp(),token,pojo.getRtsp(),openTime);
						map.put("token", cameraPojo.getToken());
						map.put("url", cameraPojo.getUrl());
						logger.info("打开：" + cameraPojo.getRtsp());
					}

				}
//		}
		return map;
	}

	/**
	 * @Title: openStream
	 * @Description: 推流器

	 * @return
	 * @return CameraPojo
	 **/
	private CameraPojo openStream(String ip,String token,String rtsp,String openTime) {
		CameraPojo cameraPojo = new CameraPojo();
		// 生成token
//		String token = UUID.randomUUID().toString();
		String rtmp = "";
//		String IP = IpUtil.IpConvert(ip);
		String url = "";
		// 历史流
//		rtsp = "rtsp://" + username + ":" + password + "@" + IP + ":554/h264/ch" + channel + "/" + stream
//				+ "/av_stream";
		String pushIp = config.getPush_host();
		String port = config.getPush_port();
		rtmp = "rtmp://" + pushIp + ":" +port+ "/live/" + token;
//		rtmp = "rtmp://" + IpUtil.IpConvert(config.getPush_host()) + ":" + config.getPush_port() + "/live/" + token;
//		if (config.getHost_extra().equals("127.0.0.1")) {
//			url = rtmp;
//		} else {
//			url = "rtmp://" + IpUtil.IpConvert(config.getHost_extra()) + ":" + config.getPush_port() + "/live/"
//					+ token;
//		}
		url = rtmp;

//		cameraPojo.setUsername(username);
//		cameraPojo.setPassword(password);
		cameraPojo.setIp(ip);
//		cameraPojo.setChannel(channel);
//		cameraPojo.setStream(stream);
		cameraPojo.setRtsp(rtsp);
		cameraPojo.setRtmp(rtmp);
		cameraPojo.setUrl(url);
		cameraPojo.setOpenTime(openTime);
		cameraPojo.setCount(1);
		cameraPojo.setToken(token);

		// 执行任务
		CameraThread.MyRunnable job = new CameraThread.MyRunnable(cameraPojo);
		CameraThread.MyRunnable.es.execute(job);
		jobMap.put(token, job);

		return cameraPojo;
	}

	/**
	 * @Title: closeCamera
	 * @Description:关闭视频流
	 * @param tokens
	 * @return void
	 **/
	@RequestMapping(value = "/cameras/{tokens}", method = RequestMethod.DELETE)
	public void closeCamera(@PathVariable("tokens") String tokens) {
		if (null != tokens && !"".equals(tokens)) {
			String[] tokenArr = tokens.split(",");
			for (String token : tokenArr) {
				if (jobMap.containsKey(token) && CacheUtil.STREAMMAP.containsKey(token)) {
					if (0 < CacheUtil.STREAMMAP.get(token).getCount()) {
						// 人数-1
						CacheUtil.STREAMMAP.get(token).setCount(CacheUtil.STREAMMAP.get(token).getCount() - 1);
						logger.info("关闭：" + CacheUtil.STREAMMAP.get(token).getRtsp() + ";当前使用人数为："
								+ CacheUtil.STREAMMAP.get(token).getCount());
					}
				}
			}
		}
	}

	/**
	 * @Title: getCameras
	 * @Description:获取视频流
	 * @return Map<String, CameraPojo>
	 **/
	@RequestMapping(value = "/cameras", method = RequestMethod.GET)
	public Map<String, CameraPojo> getCameras() {
		logger.info("获取视频流信息：" + CacheUtil.STREAMMAP.toString());
		return CacheUtil.STREAMMAP;
	}

	/**
	 * @Title: keepAlive
	 * @Description:视频流保活
	 * @param tokens
	 * @return void
	 **/
	@RequestMapping(value = "/cameras/{tokens}", method = RequestMethod.PUT)
	public void keepAlive(@PathVariable("tokens") String tokens) {
		// 校验参数
		if (null != tokens && !"".equals(tokens)) {
			String[] tokenArr = tokens.split(",");
			for (String token : tokenArr) {
				CameraPojo cameraPojo = new CameraPojo();
				// 直播流token
				if (null != CacheUtil.STREAMMAP.get(token)) {
					cameraPojo = CacheUtil.STREAMMAP.get(token);
					// 更新当前系统时间
					cameraPojo.setOpenTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date().getTime()));
					logger.info("视频流：" + cameraPojo.getRtmp() + "保活！");
				}
			}
		}
	}

	/**
	 * @Title: getConfig
	 * @Description: 获取服务信息
	 * @return Map<String, Object>
	 **/
	@RequestMapping(value = "/status", method = RequestMethod.GET)
	public Map<String, Object> getConfig() {
		// 获取当前时间
		long nowTime = new Date().getTime();
		String upTime = (nowTime - CacheUtil.STARTTIME) / (1000 * 60 * 60) + "h"
				+ (nowTime - CacheUtil.STARTTIME) % (1000 * 60 * 60) / (1000 * 60) + "m"
				+ (nowTime - CacheUtil.STARTTIME) % (1000 * 60 * 60) / (1000) + "s";
		logger.info("获取服务信息：" + config.toString() + ";服务运行时间：" + upTime);
		Map<String, Object> status = new HashMap<String, Object>();
		status.put("config", config);
		status.put("uptime", upTime);
		return status;
	}

}
