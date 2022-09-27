package com.springstudymvc.msb.other.spi;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/7/18 12:10 上午
 */
public class Dog implements IShout {
	@Override
	public void shout() {
		System.out.println("wang wang");
	}

}