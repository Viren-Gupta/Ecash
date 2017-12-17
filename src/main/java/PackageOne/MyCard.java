package PackageOne;


public class MyCard {
	private int cardNum;
	private double balance;
	private MyTransaction lastTransaction;
	
	MyCard(int cardNum,double balance,MyTransaction lastTransaction){
		this.cardNum=cardNum;
		this.balance=balance;
		this.lastTransaction=lastTransaction;
	}

	public double getBalance() {
		return balance;
	}

	public void setBalance(double balance) {
		this.balance = balance;
	}

	public MyTransaction getLastTransaction() {
		return lastTransaction;
	}

	public void setLastTransaction(MyTransaction lastTransaction) {
		this.lastTransaction = lastTransaction;
	}

	public int getCardNum() {
		return cardNum;
	}

	public void setCardNum(int cardNum) {
		this.cardNum = cardNum;
	}
	
	
}
