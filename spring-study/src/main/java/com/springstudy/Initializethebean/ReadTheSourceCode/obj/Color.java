package com.springstudy.Initializethebean.ReadTheSourceCode.obj;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/9/4 5:26 下午
 */
@Component
public class Color {

	@PostConstruct
	public void init(){
		System.out.println("init......");
	}

	@PreDestroy
	public void destroy(){
		System.out.println("destroy.........");
	}

}