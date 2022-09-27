package com.springstudy.msb.s_12.obverser.test02;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description main
 * @date 2022/4/29 10:08 上午
 */
public class Main {

	/**
	 * 对test02做的优化
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