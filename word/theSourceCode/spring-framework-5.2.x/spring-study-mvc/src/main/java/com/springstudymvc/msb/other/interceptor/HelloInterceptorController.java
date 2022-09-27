package com.springstudymvc.msb.other.interceptor;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/8/7 6:14 下午
 */
@Controller
public class HelloInterceptorController {

	@RequestMapping("/hello/interceptor")
	@ResponseBody
	public String hello() {
		System.out.println("你好，我是handler....");
		return "执行成功";
	}

}