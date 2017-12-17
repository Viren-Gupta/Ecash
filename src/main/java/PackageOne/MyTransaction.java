package PackageOne;

public class MyTransaction {
	private String location;
	private double amount;
	private String date;
	private String itemName;
	private int quantity;
	
	public String getItemName() {
		return itemName;
	}

	public void setItemName(String itemName) {
		this.itemName = itemName;
	}

	public MyTransaction(String location, double amount, String date,
			String itemName, int quantity) {
		this.location = location;
		this.amount = amount;
		this.date = date;
		this.itemName = itemName;
		this.quantity = quantity;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	
	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public double getAmount() {
		return amount;
	}

	public void setAmount(double amount) {
		this.amount = amount;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}
	
}

