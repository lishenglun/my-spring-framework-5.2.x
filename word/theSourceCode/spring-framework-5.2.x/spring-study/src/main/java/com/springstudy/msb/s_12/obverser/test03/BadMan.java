package com.springstudy.msb.s_12.obverser.test03;

import java.util.Observable;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 * @date 2022/4/29 10:20 上午
 */
public class BadMan extends Observable {

	public void run() {
		System.out.println("罪犯要逃跑了");
		// ⚠️告诉观察者要做什么
		setChanged();
		notifyObservers("追击罪犯");
	}

	public void play() {
		System.out.println("罪犯在玩");
		// ⚠️告诉观察者要做什么
		setChanged();
		notifyObservers("不做任何事情，静观其变");
	}

}