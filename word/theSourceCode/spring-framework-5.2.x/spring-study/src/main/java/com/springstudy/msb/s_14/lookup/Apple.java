package com.springstudy.msb.s_14.lookup;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/4 10:40 上午
 */
public class Apple extends Fruit {


	private Banana banana;

	public Apple() {
		System.out.println("I got a fresh apple");
	}

	//@Lookup
	//public Banana getBanana() {
	//	return banana;
	//}
	//
	//public void setBanana(Banana banana) {
	//	this.banana = banana;
	//}

}