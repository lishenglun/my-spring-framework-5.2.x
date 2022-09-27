package com.springstudy.Initializethebean.primary;

import org.springframework.stereotype.Component;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/31 4:57 下午
 */
@Component // 加注解，让spring识别
public class MetalSinger implements Singer {

	@Override
	public String sing(String lyrics) {
		return "I am singing with DIO voice: "+lyrics;
	}
}