package com.springstudy.msb.s_12.obverser.test01;


/**
 * @author lishenglun
 * @version v1.0.0
 * @description 观察者模式：不会显示的调用观察者的方法，而是显示的调用被观察者，然后被观察者内部调用观察者的方法
 * @date 2022/4/29 10:08 上午
 */
public class Main {

	public static void main(String[] args) {
		// 被观察者
		BadMan badMan = new BadMan();

		// 观察者
		Observer police = new Police();
		Observer soldier = new Soldier();

		badMan.addObserver(police);
		badMan.addObserver(soldier);

		/* 没有显示调用观察者的方法，而是调用被观察者的方法，在被观察者方法的内部会触发调用观察者的方法！ */
		badMan.run();
	}

}