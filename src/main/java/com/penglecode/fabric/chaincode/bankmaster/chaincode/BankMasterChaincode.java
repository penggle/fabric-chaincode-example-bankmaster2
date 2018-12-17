package com.penglecode.fabric.chaincode.bankmaster.chaincode;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.penglecode.fabric.chaincode.bankmaster.domain.AccountTransactionRecord;
import com.penglecode.fabric.chaincode.bankmaster.domain.AccountTransactionType;
import com.penglecode.fabric.chaincode.bankmaster.domain.CustomerAccount;
import com.penglecode.fabric.chaincode.common.util.BankUtils;
import com.penglecode.fabric.chaincode.common.util.DateTimeUtils;
import com.penglecode.fabric.chaincode.common.util.JsonUtils;

public class BankMasterChaincode extends ChaincodeBase {

	private static final Logger LOGGER = LoggerFactory.getLogger(BankMasterChaincode.class);
	
	private static final Charset CHARSET = StandardCharsets.UTF_8;
	
	private static final Double DEFAULT_ACCOUNT_BALANCE = 0.0;
	
	private static final String BANK_CARD_PREFIX = "6225";
	
	private static final String KEY_BANK_BALANCE = "BANK_BALANCE";
	
	private static final String KEY_PREFIX_CUSTOMER_ACCOUNT = "CUSTOMER_ACCOUNT_";
	
	private static final String KEY_PREFIX_ACCOUNT_TRANSACTION_RECORD = "ACCOUNT_TRANSACTION_RECORD_";
	
	/**
	 * 智能合约初始化
	 * 参数列表：parameters[0] = 100		<银行资产金额>
	 */
	@Override
	public Response init(ChaincodeStub stub) {
		List<String> parameters = stub.getParameters();
		LOGGER.info(">>> parameters = {}, args = {}", stub.getParameters(), stub.getStringArgs());
		Double bankBalance = DEFAULT_ACCOUNT_BALANCE;
		if(parameters.size() == 1 && NumberUtils.isCreatable(StringUtils.trimToEmpty(parameters.get(0))) 
				&& (bankBalance = Double.parseDouble(StringUtils.trimToEmpty(parameters.get(0)))) > 0) {
			stub.putStringState(KEY_BANK_BALANCE, bankBalance.toString()); //初始化银行资产
			return newSuccessResponse("初始化智能合约成功!");
        } else {
        	return newErrorResponse("初始化智能合约失败：参数只能有一个，并且为非负数值类型数据!");
        }
	}

	/**
	 * 调用智能合约
	 */
	@Override
	public Response invoke(ChaincodeStub stub) {
		String function = stub.getFunction();
        List<String> args = stub.getParameters();
        LOGGER.info(">>> 调用智能合约开始，function = {}, args = {}", function, args);
        Response response = null;
        try {
	        response = doInvoke(stub, function, args);
        } catch (Throwable e) {
        	LOGGER.error(e.getMessage(), e);
        	response = newErrorResponse(String.format("调用智能合约出错：%s", ExceptionUtils.getRootCauseMessage(e)));
        }
        LOGGER.info("<<< 调用智能合约结束，response = [status = {}, message = {}, payload = {}]", response.getStatus().getCode(), response.getMessage(), response.getPayload() == null ? null : new String(response.getPayload(), CHARSET));
        return response;
	}
	
	protected Response doInvoke(ChaincodeStub stub, String function, List<String> args) throws Exception {
		if("createAccount".equals(function)) {
        	return createAccount(stub, args);
        } else if ("depositMoney".equals(function)) {
        	return depositMoney(stub, args);
        } else if ("drawalMoney".equals(function)) {
        	return drawalMoney(stub, args);
        } else if ("transferAccount".equals(function)) {
        	return transferAccount(stub, args);
        } else if ("getAccountBalance".equals(function)) {
        	return getAccountBalance(stub, args);
        } else if ("getAllAccountList".equals(function)) {
        	return getAllAccountList(stub, args);
        }
		return newErrorResponse(String.format("不存在的智能合约方法名: %s", function));
	}
	
	/**
	 * 客户开户
	 * 参数列表：parameters[0] = {"accountNo":null,"realName":"彭三","idCardNo":"342425198607284712","mobilePhone":"15151887280"} 		<客户资料json>
	 * @param stub
	 * @param args
	 * @return
	 * @throws Exception
	 */
	protected Response createAccount(ChaincodeStub stub, List<String> args) throws Exception {
		String requestBody = null;
		if(args.size() == 1 && JsonUtils.isJsonObject((requestBody = args.get(0)))) {
			CustomerAccount account = JsonUtils.json2Object(requestBody, CustomerAccount.class);
			if(StringUtils.isBlank(account.getRealName())) {
				return newErrorResponse("请求参数不合法：开户人真实姓名不能为空!");
			}
			if(StringUtils.isBlank(account.getIdCardNo())) {
				return newErrorResponse("请求参数不合法：开户人身份证号码不能为空!");
			}
			if(StringUtils.isBlank(account.getMobilePhone())) {
				return newErrorResponse("请求参数不合法：开户人手机号码不能为空!");
			}
			String nowTime = DateTimeUtils.formatNow();
			account.setAccountBalance(ObjectUtils.defaultIfNull(account.getAccountBalance(), DEFAULT_ACCOUNT_BALANCE));
			account.setCreatedTime(nowTime);
			account.setAccountNo(BankUtils.genBankCardNo(BANK_CARD_PREFIX));
			account.setLatestTransactionType(AccountTransactionType.CREATE_ACCOUNT.name());
			
			String jsonAccount = saveCustomerAccount(stub, account); //保存账户
			
			saveBankBalance(stub, account.getAccountBalance()); //保存银行余额
			
			return newSuccessResponse("开户成功!", jsonAccount.getBytes(CHARSET));
		} else {
			return newErrorResponse("请求参数不合法：参数只能有一个，并且为json类型数据!");
		}
	}
	
	/**
	 * 客户存款
	 * 参数列表：parameters[0] = 6225778834761431			<客户账户卡号>
	 * 			 parameters[1] = 500						<存款金额>
	 * @param stub
	 * @param args
	 * @return
	 * @throws Exception
	 */
	protected synchronized Response depositMoney(ChaincodeStub stub, List<String> args) throws Exception {
		String accountNo = null;
		String amountValue = null;
		if(args.size() == 2) {
			accountNo = StringUtils.trimToEmpty(args.get(0));
			if(!accountNo.matches("\\d{16}")) {
				return newErrorResponse("请求参数不合法：第一个参数为账户卡号，必须是16位银行卡号!");
			}
			amountValue = StringUtils.trimToEmpty(args.get(1));
			if(!NumberUtils.isCreatable(amountValue)) {
				return newErrorResponse("请求参数不合法：第二个参数为存款金额，必须是大于0的数值类型!");
			}
			Double amount = Double.valueOf(amountValue);
			if(amount <= 0) {
				return newErrorResponse("请求参数不合法：第二个参数为存款金额，必须是大于0的数值类型!");
			}
			
			CustomerAccount account = getCustomerAccountByNo(stub, accountNo);
			if(account == null) {
				return newErrorResponse(String.format("对不起，账号(%s)不存在!", accountNo));
			}
			double balance = account.getAccountBalance();
			account.setAccountBalance(balance + amount); //更新余额
			account.setLatestTransactionType(AccountTransactionType.DEPOSITE_MONEY.name());
			
			saveCustomerAccount(stub, account); //保存账户
			
			saveBankBalance(stub, amount); //保存银行余额
			
			return newSuccessResponse("存款成功!", account.getAccountBalance().toString().getBytes(CHARSET));
		} else {
			return newErrorResponse("请求参数不合法：参数只能有两个!");
		}
	}
	
	/**
	 * 客户取款
	 * 参数列表：parameters[0] = 6225778834761431			<客户账户卡号>
	 * 			 parameters[1] = 500						<取款金额>
	 * @param stub
	 * @param args
	 * @return
	 * @throws Exception
	 */
	protected synchronized Response drawalMoney(ChaincodeStub stub, List<String> args) throws Exception {
		String accountNo = null;
		String amountValue = null;
		if(args.size() == 2) {
			accountNo = StringUtils.trimToEmpty(args.get(0));
			if(!accountNo.matches("\\d{16}")) {
				return newErrorResponse("请求参数不合法：第一个参数为账户卡号，必须是16位银行卡号!");
			}
			amountValue = StringUtils.trimToEmpty(args.get(1));
			if(!NumberUtils.isCreatable(amountValue)) {
				return newErrorResponse("请求参数不合法：第二个参数为取款金额，必须是大于0的数值类型!");
			}
			Double amount = Double.valueOf(amountValue);
			if(amount <= 0) {
				return newErrorResponse("请求参数不合法：第二个参数为取款金额，必须是大于0的数值类型!");
			}
			
			CustomerAccount account = getCustomerAccountByNo(stub, accountNo);
			if(account == null) {
				return newErrorResponse(String.format("对不起，账号(%s)不存在!", accountNo));
			}
			double balance = account.getAccountBalance();
			account.setAccountBalance(balance - amount); //更新余额
			account.setLatestTransactionType(AccountTransactionType.DRAWAL_MONEY.name());
			
			saveCustomerAccount(stub, account); //保存账户
			
			saveBankBalance(stub, -amount); //保存银行余额
			
			return newSuccessResponse("取款成功!", account.getAccountBalance().toString().getBytes(CHARSET));
		} else {
			return newErrorResponse("请求参数不合法：参数只能有两个!");
		}
	}
	
	/**
	 * 客户转账
	 * 参数列表：parameters[0] = 6225778834761431			<转出账户卡号>
	 * 			 parameters[1] = 6225778834761432			<转入账户卡号>
	 * 			 parameters[2] = 500						<转账金额>
	 * @param stub
	 * @param args
	 * @return
	 * @throws Exception
	 */
	protected synchronized Response transferAccount(ChaincodeStub stub, List<String> args) throws Exception {
		String accountANo = null, accountBNo = null;
		String amountValue = null;
		if(args.size() == 3) {
			accountANo = StringUtils.trimToEmpty(args.get(0));
			if(!accountANo.matches("\\d{16}")) {
				return newErrorResponse("请求参数不合法：第一个参数为转出账户卡号，必须是16位银行卡号!");
			}
			accountBNo = StringUtils.trimToEmpty(args.get(1));
			if(!accountBNo.matches("\\d{16}")) {
				return newErrorResponse("请求参数不合法：第二个参数为转入账户卡号，必须是16位银行卡号!");
			}
			amountValue = StringUtils.trimToEmpty(args.get(2));
			if(!NumberUtils.isCreatable(amountValue)) {
				return newErrorResponse("请求参数不合法：第三个参数为转账金额，必须是大于0的数值类型!");
			}
			Double amount = Double.valueOf(amountValue);
			if(amount <= 0) {
				return newErrorResponse("请求参数不合法：第三个参数为转账金额，必须是大于0的数值类型!");
			}
			
			CustomerAccount accountA = getCustomerAccountByNo(stub, accountANo);
			if(accountA == null) {
				return newErrorResponse(String.format("对不起，转出账号(%s)不存在!", accountANo));
			}
			CustomerAccount accountB = getCustomerAccountByNo(stub, accountBNo);
			if(accountB == null) {
				return newErrorResponse(String.format("对不起，转入账号(%s)不存在!", accountBNo));
			}
			double balanceA = accountA.getAccountBalance();
			accountA.setAccountBalance(balanceA - amount); //更新余额
			accountA.setLatestTransactionType(AccountTransactionType.TRANSFER_OUT.name());
			
			double balanceB = accountB.getAccountBalance();
			accountB.setAccountBalance(balanceB + amount); //更新余额
			accountB.setLatestTransactionType(AccountTransactionType.TRANSFER_IN.name());
			
			saveCustomerAccount(stub, accountA); //保存账户
			
			saveBankBalance(stub, -amount); //保存银行余额
			
			saveCustomerAccount(stub, accountB); //保存账户
			
			saveBankBalance(stub, amount); //保存银行余额
			
			return newSuccessResponse("转账成功!", accountA.getAccountBalance().toString().getBytes(CHARSET));
		} else {
			return newErrorResponse("请求参数不合法：参数只能有三个!");
		}
	}
	
	/**
	 * 查询账户余额
	 * 参数列表：parameters[0] = 6225778834761431			<账户卡号>
	 * @param stub
	 * @param args
	 * @return
	 * @throws Exception
	 */
	protected Response getAccountBalance(ChaincodeStub stub, List<String> args) throws Exception {
		String accountNo = null;
		if(args.size() == 1 && (accountNo = StringUtils.trimToEmpty(args.get(0))).matches("\\d{16}")) {
			CustomerAccount account = getCustomerAccountByNo(stub, accountNo);
			if(account == null) {
				return newErrorResponse(String.format("对不起，账号(%s)不存在!", accountNo));
			}
			return newSuccessResponse("查询余额成功!", account.getAccountBalance().toString().getBytes(CHARSET));
		} else {
			return newErrorResponse("请求参数不合法：参数只能有一个，且必须是16位银行卡号!");
		}
	}
	
	/**
	 * 查询所有账户列表
	 * @param stub
	 * @param args
	 * @return
	 * @throws Exception
	 */
	protected Response getAllAccountList(ChaincodeStub stub, List<String> args) throws Exception {
		List<String> accounts = new ArrayList<String>();
		String compositeKey = stub.createCompositeKey(KEY_PREFIX_CUSTOMER_ACCOUNT).toString();
		QueryResultsIterator<KeyValue> results = stub.getStateByPartialCompositeKey(compositeKey);
		for(Iterator<KeyValue> it = results.iterator(); it.hasNext();) {
			KeyValue kv = it.next();
			accounts.add(kv.getStringValue());
		}
		results.close();
		String payload = "[" + StringUtils.join(accounts, ",") + "]";
		return newSuccessResponse("查询所有账户列表成功!", payload.getBytes(CHARSET));
	}
	
	/**
	 * 查询账户的最近多少条交易记录
	 * 参数列表：parameters[0] = 6225778834761431			<账户卡号>
	 * 			 parameters[1] = 10							<返回记录条数>
	 * @param stub
	 * @param args
	 * @return
	 * @throws Exception
	 */
	protected Response getAccountTransactionRecordList(ChaincodeStub stub, List<String> args) throws Exception {
		String accountNo = null;
		int fetchCount = 10;
		if(CollectionUtils.isEmpty(args)) {
			return newErrorResponse("请求参数不合法：至少需要1个参数(16位银行卡号)!");
		} else if ((accountNo = StringUtils.trimToEmpty(args.get(0))).matches("\\d{16}")) {
			return newErrorResponse("请求参数不合法：第1个参数必须是16位银行卡号!");
		} else if (args.size() > 2) {
			return newErrorResponse("请求参数不合法：参数最多只能有2个，且第一个是16位银行卡号、第2个是返回记录条数!");
		} else {
			if(args.size() <= 2) {
				try {
					fetchCount = Integer.valueOf(StringUtils.trim(args.get(1)));
				} catch (Exception e) {}
			}
			String key = customerAccountKey(stub, accountNo);
			QueryResultsIterator<KeyModification> qrIterator = stub.getHistoryForKey(key);
			List<KeyModification> keyModifications = new ArrayList<KeyModification>();
			int count = 0;
			for(Iterator<KeyModification> it = qrIterator.iterator(); it.hasNext();) {
				keyModifications.add(it.next());
				if(count++ == fetchCount) {
					break;
				}
			}
		}
		return null;
	}
	
	protected List<AccountTransactionRecord> calculateAccountTransactionRecords(List<KeyModification> keyModifications) {
		List<AccountTransactionRecord> recordList = new ArrayList<AccountTransactionRecord>();
		if(!CollectionUtils.isEmpty(keyModifications)) {
			int len = keyModifications.size();
			KeyModification currentKeyMod = null, prevKeyMod = null;
			for(int i = 0; i < len; i++) {
				currentKeyMod = keyModifications.get(i);
				if(i > 0) {
					prevKeyMod = keyModifications.get(i - 1);
				}
				CustomerAccount currentAccount = JsonUtils.json2Object(currentKeyMod.getStringValue(), CustomerAccount.class);
				CustomerAccount prevAccount = prevKeyMod == null ? null : JsonUtils.json2Object(prevKeyMod.getStringValue(), CustomerAccount.class);
				
				AccountTransactionRecord record = new AccountTransactionRecord();
				record.setTransactionId(currentKeyMod.getTxId());
				record.setTransactionAccountNo(currentAccount.getAccountNo());
				record.setBeforeAccountBalance(prevAccount == null ? 0.0 : prevAccount.getAccountBalance());
				record.setAfterAccountBalance(currentAccount.getAccountBalance());
				record.setTransactionBalance(record.getAfterAccountBalance() - record.getBeforeAccountBalance());
				AccountTransactionType transactionType = AccountTransactionType.getTransactionType(currentAccount.getLatestTransactionType());
				record.setTransactionType(transactionType.name());
				record.setTransactionDesc(transactionType.getDescription());
				String transactionTime = DateTimeUtils.format(LocalDateTime.ofInstant(currentKeyMod.getTimestamp(), ZoneId.systemDefault()), DateTimeUtils.DEFAULT_DATETIME_PATTERN);
				record.setTransactionTime(transactionTime);
			}
		}
		return null;
	}
	
	protected String customerAccountKey(ChaincodeStub stub, String accountNo) {
		return stub.createCompositeKey(KEY_PREFIX_CUSTOMER_ACCOUNT, accountNo).toString();
	}
	
	protected CustomerAccount getCustomerAccountByNo(ChaincodeStub stub, String accountNo) {
		String key = customerAccountKey(stub, accountNo);
		String value = stub.getStringState(key);
		if(!StringUtils.isEmpty(value)) {
			return JsonUtils.json2Object(value, CustomerAccount.class);
		}
		return null;
	}
	
	protected String saveCustomerAccount(ChaincodeStub stub, CustomerAccount account) {
		String jsonAccount = JsonUtils.object2Json(account);
		stub.putStringState(customerAccountKey(stub, account.getAccountNo()), jsonAccount); //修改账本
		return jsonAccount;
	}
	
	protected Double saveBankBalance(ChaincodeStub stub, Double delta) {
		Double bankBalance = Double.valueOf(stub.getStringState(KEY_BANK_BALANCE));
		bankBalance = bankBalance + delta;
		stub.putStringState(KEY_BANK_BALANCE, String.valueOf(bankBalance));
		return bankBalance;
	}
	
	public static void main(String[] args) {
		LOGGER.info(">>> Chaincode starting, args = {}", Arrays.toString(args));
        new BankMasterChaincode().start(args);
    }

}
