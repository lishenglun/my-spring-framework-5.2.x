package com.springstudy.msb.s_21;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/5/30 10:44 下午
 */
//@Service
public class MyCalculator {

	public Integer add(Integer i, Integer j) throws NoSuchMethodException {
		Integer result = i + j;
		return result;
	}

	public Integer sub(Integer i, Integer j) throws NoSuchMethodException {
		Integer result = i - j;
		return result;
	}

	public Integer mul(Integer i, Integer j) throws NoSuchMethodException {
		Integer result = i * j;
		return result;
	}

	public Integer div(Integer i, Integer j) throws NoSuchMethodException {
		Integer result = i / j;
		return result;
	}

	public Integer show(Integer i) {
		System.out.println("show ......");
		return i;
	}

}