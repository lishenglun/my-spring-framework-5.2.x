package com.springstudy.msb.s_12.obverser.test01;


/**
 * @author lishenglun
 * @version v1.0.0
 * @description 观察者：警察
 * @date 2022/4/29 10:03 上午
 */
public class Police implements Observer {

	@Override
	public void make(String str) {
		System.out.println("警察开始" + str);
	}

}