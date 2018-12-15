package com.penglecode.fabric.chaincode.bankmaster.domain;

import java.io.Serializable;

/**
 * 客户账户交易记录
 * 
 * @author 	pengpeng
 * @date	2018年12月14日 下午5:24:55
 */
public class AccountTransactionRecord implements Serializable {

	private static final long serialVersionUID = 1L;
	
	/**
	 * 交易账户
	 */
	private String transactionAccountNo;
	
	/**
	 * 交易类型
	 */
	private AccountTransactionType transactionType;
	
	/**
	 * 账户交易前金额
	 */
	private Double beforeAccountBalance;
	
	/**
	 * 账户交易后金额
	 */
	private Double afterAccountBalance;
	
	/**
	 * 交易金额
	 */
	private Double transactionBalance;
	
	/**
	 * 如果是转账类型，则为对方账号
	 */
	private String transferAccountNo;
	
	/**
	 * 交易时间
	 */
	private String transactionTime;

	public AccountTransactionRecord() {
		super();
	}

	public AccountTransactionRecord(String transactionAccountNo, AccountTransactionType transactionType,
			Double beforeAccountBalance, Double afterAccountBalance, Double transactionBalance,
			String transferAccountNo, String transactionTime) {
		super();
		this.transactionAccountNo = transactionAccountNo;
		this.transactionType = transactionType;
		this.beforeAccountBalance = beforeAccountBalance;
		this.afterAccountBalance = afterAccountBalance;
		this.transactionBalance = transactionBalance;
		this.transferAccountNo = transferAccountNo;
		this.transactionTime = transactionTime;
	}

	public String getTransactionAccountNo() {
		return transactionAccountNo;
	}

	public void setTransactionAccountNo(String transactionAccountNo) {
		this.transactionAccountNo = transactionAccountNo;
	}

	public AccountTransactionType getTransactionType() {
		return transactionType;
	}

	public void setTransactionType(AccountTransactionType transactionType) {
		this.transactionType = transactionType;
	}

	public Double getBeforeAccountBalance() {
		return beforeAccountBalance;
	}

	public void setBeforeAccountBalance(Double beforeAccountBalance) {
		this.beforeAccountBalance = beforeAccountBalance;
	}

	public Double getAfterAccountBalance() {
		return afterAccountBalance;
	}

	public void setAfterAccountBalance(Double afterAccountBalance) {
		this.afterAccountBalance = afterAccountBalance;
	}

	public Double getTransactionBalance() {
		return transactionBalance;
	}

	public void setTransactionBalance(Double transactionBalance) {
		this.transactionBalance = transactionBalance;
	}

	public String getTransferAccountNo() {
		return transferAccountNo;
	}

	public void setTransferAccountNo(String transferAccountNo) {
		this.transferAccountNo = transferAccountNo;
	}

	public String getTransactionTime() {
		return transactionTime;
	}

	public void setTransactionTime(String transactionTime) {
		this.transactionTime = transactionTime;
	}
	
}
