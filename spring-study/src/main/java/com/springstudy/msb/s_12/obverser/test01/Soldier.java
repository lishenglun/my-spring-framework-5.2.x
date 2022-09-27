package com.springstudy.msb.s_12.obverser.test01;


/**
 * @author lishenglun
 * @version v1.0.0
 * @description 观察者：军人
 * @date 2022/4/29 10:06 上午
 */
public class Soldier implements Observer {

	@Override
	public void make(String str) {
		System.out.println("军人开始" + str);
	}

}