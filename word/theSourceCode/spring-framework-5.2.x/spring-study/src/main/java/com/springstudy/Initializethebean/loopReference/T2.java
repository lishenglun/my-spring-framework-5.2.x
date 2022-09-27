package com.springstudy.Initializethebean.loopReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/9/1 3:26 下午
 */
@Service
public class T2 {

	@Autowired
	private T1 t1;

}