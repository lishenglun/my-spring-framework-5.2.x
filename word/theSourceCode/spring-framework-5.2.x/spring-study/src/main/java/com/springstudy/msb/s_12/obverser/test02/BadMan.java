package com.springstudy.msb.s_12.obverser.test02;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 被观察者：罪犯
 * @date 2022/4/29 9:56 上午
 */
public class BadMan extends Observable {

	public void run() {
		System.out.println("罪犯要逃跑了");
		// ⚠️告诉观察者要做什么
		notifyAllObserver("追击罪犯");
	}

	public void play() {
		System.out.println("罪犯在玩");
		// ⚠️告诉观察者要做什么
		notifyAllObserver("不做任何事情，静观其变");
	}

}