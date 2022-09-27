package com.springstudy.msb.s_12.obverser.test02;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 被观察者的抽象类
 * @date 2022/4/29 9:57 上午
 */
public class Observable {

	private List<Observer> observerList = new ArrayList<>();

	public void addObserver(Observer observer) {
		observerList.add(observer);
	}

	public void delObserver(Observer observer) {
		observerList.remove(observer);
	}

	public void notifyAllObserver(String str) {
		for (Observer observer : observerList) {
			observer.make(str);
		}
	}

}