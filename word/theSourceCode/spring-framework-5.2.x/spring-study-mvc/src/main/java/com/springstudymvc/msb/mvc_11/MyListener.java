package com.springstudymvc.msb.mvc_11;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import java.io.IOException;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/2 8:39 下午
 */
public class MyListener implements AsyncListener {

	@Override
	public void onComplete(AsyncEvent event) throws IOException {
		System.out.println("完成了");
	}

	@Override
	public void onTimeout(AsyncEvent event) throws IOException {
		System.out.println("超时了");
	}

	@Override
	public void onError(AsyncEvent event) throws IOException {
		System.out.println("错误了");
	}

	@Override
	public void onStartAsync(AsyncEvent event) throws IOException {
		System.out.println("开始处理");
	}

}