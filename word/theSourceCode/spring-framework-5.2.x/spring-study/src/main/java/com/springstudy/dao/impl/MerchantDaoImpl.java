package com.springstudy.dao.impl;

import com.springstudy.dao.MerchantDao;
import org.springframework.stereotype.Service;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/22 11:40 下午
 */
@Service
public class MerchantDaoImpl implements MerchantDao {

	@Override
	public void selectMerchantInfoList() {
		System.out.println("MerchantDaoImpl selectMerchantInfoList ...");
	}
}