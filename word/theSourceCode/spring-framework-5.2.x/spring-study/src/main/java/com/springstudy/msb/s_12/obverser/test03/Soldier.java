package com.springstudy.msb.s_12.obverser.test03;


import java.util.Observable;
import java.util.Observer;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/29 10:21 上午
 */
public class Soldier implements Observer {

	@Override
	public void update(Observable o, Object arg) {
		System.out.println("军人开始" + arg);
	}

}