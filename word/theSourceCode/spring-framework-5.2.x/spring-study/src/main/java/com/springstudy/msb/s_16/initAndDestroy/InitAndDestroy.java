package com.springstudy.msb.s_16.initAndDestroy;

import javax.annotation.PostConstruct;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/12 10:44 下午
 */
//@Component
public class InitAndDestroy {

	private String name;

	@PostConstruct
	public void init() {
		System.out.println("init...............");
	}

	@PostConstruct
	public void init2() {
		System.out.println("init2...............");
	}

	//@PreDestroy
	public void destroy() {
		System.out.println("destroy............");
	}

}