package com.springstudy.msb.s_12.obverser.test01;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description 被观察者的抽象接口
 * @date 2022/4/29 10:10 上午
 */
public interface Observable {

	void addObserver(Observer observer);

	void delObserver(Observer observer);

	void notifyAllObserver(String str);

}