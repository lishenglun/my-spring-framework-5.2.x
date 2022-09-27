package com.springstudy.msb.s_12.obverser.test03;


import java.util.Observer;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2022/4/29 10:21 上午
 */
public class Main {

	// SPI：

	/**
	 * 使用jdk的方式来进行实现观察者模式
	 * @param args
	 */
	public static void main(String[] args) {
		// 被观察者
		BadMan badMan = new BadMan();

		// 观察者
		Observer police = new Police();
		Observer soldier = new Soldier();

		badMan.addObserver(police);
		badMan.addObserver(soldier);

		badMan.run();
	}

}