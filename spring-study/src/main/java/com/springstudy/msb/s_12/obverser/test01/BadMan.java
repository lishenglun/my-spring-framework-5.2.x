package com.springstudy.msb.s_12.obverser.test01;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 被观察者：罪犯
 * @date 2022/4/29 10:11 上午
 */
public class BadMan implements Observable {

	private List<Observer> observerList = new ArrayList<>();

	@Override
	public void addObserver(Observer observer) {
		observerList.add(observer);
	}

	@Override
	public void delObserver(Observer observer) {
		observerList.remove(observer);
	}

	@Override
	public void notifyAllObserver(String str) {
		for (Observer observer : observerList) {
			observer.make(str);
		}
	}

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