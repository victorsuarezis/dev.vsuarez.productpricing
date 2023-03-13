/**
 * 
 */
package dev.vsuarez.model;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.logging.Level;

import org.compiere.model.MDiscountSchema;
import org.compiere.model.MDiscountSchemaBreak;
import org.compiere.util.Env;

/**
 * @author <a href="mailto:victor.suarez.is@gmail.com">Ing. Victor Suarez</a>
 *
 */
public class IVS_MDiscountSchema extends MDiscountSchema {

	/**
	 * 
	 */
	private static final long serialVersionUID = -454061213377618696L;
	
	/** Cascade = C */
	public static final String DISCOUNTTYPE_Cascade = "C";

	/**
	 * @param ctx
	 * @param M_DiscountSchema_ID
	 * @param trxName
	 */
	public IVS_MDiscountSchema(Properties ctx, int M_DiscountSchema_ID, String trxName) {
		super(ctx, M_DiscountSchema_ID, trxName);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param ctx
	 * @param rs
	 * @param trxName
	 */
	public IVS_MDiscountSchema(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param copy
	 */
	public IVS_MDiscountSchema(MDiscountSchema copy) {
		super(copy);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param ctx
	 * @param copy
	 */
	public IVS_MDiscountSchema(Properties ctx, MDiscountSchema copy) {
		super(ctx, copy);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param ctx
	 * @param copy
	 * @param trxName
	 */
	public IVS_MDiscountSchema(Properties ctx, MDiscountSchema copy, String trxName) {
		super(ctx, copy, trxName);
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * 	Calculate Discounted Price
	 *	@param Qty quantity
	 *	@param Price price
	 *	@param M_Product_ID product
	 *	@param M_Product_Category_ID category
	 *	@param BPartnerFlatDiscount flat discount
	 *  @param M_Warehouse_ID warehouse
	 *  @param M_PriceList_ID Price List
	 *	@return discount or zero
	 */
	public BigDecimal calculatePrice (BigDecimal Qty, BigDecimal Price, int M_Product_ID, int M_Product_Category_ID,  
		BigDecimal BPartnerFlatDiscount, int M_Warehouse_ID, int M_PriceList_ID) {
		if (log.isLoggable(Level.FINE)) log.fine("Price=" + Price + ",Qty=" + Qty);
		if (Price == null || Env.ZERO.compareTo(Price) == 0)
			return Price;
		//
		BigDecimal discount = calculateDiscount(Qty, Price, M_Product_ID, M_Product_Category_ID, 
				BPartnerFlatDiscount, M_Warehouse_ID, M_PriceList_ID);
		//	nothing to calculate
		if (discount == null || discount.signum() == 0) {
			BigDecimal fixedPrice = calculateFixedPrice(Qty, Price, M_Product_ID, M_Product_Category_ID,
					BPartnerFlatDiscount, M_Warehouse_ID, M_PriceList_ID);
			if (fixedPrice != null)
				return fixedPrice;
			else
				return Price;
		}
		//
		BigDecimal newPrice = calculateDiscountedPrice(Price, discount);
		if (log.isLoggable(Level.FINE)) log.fine("=>" + newPrice);
		return newPrice;

	}	//	calculatePrice
	
	/**
	 * 	Calculate Discount Percentage
	 *	@param Qty quantity
	 *	@param Price price
	 *	@param M_Product_ID product
	 *	@param M_Product_Category_ID category
	 *	@param BPartnerFlatDiscount flat discount
	 *  @param M_Warehouse_ID warehouse
	 *  @param M_PriceList_ID Price List
	 *	@return discount or zero
	 */
	public BigDecimal calculateDiscount (BigDecimal Qty, BigDecimal Price, int M_Product_ID, int M_Product_Category_ID,
		BigDecimal BPartnerFlatDiscount, int M_Warehouse_ID, int M_PriceList_ID) {
		if (BPartnerFlatDiscount == null)
			BPartnerFlatDiscount = Env.ZERO;
		
		//
		if (DISCOUNTTYPE_FlatPercent.equals(getDiscountType()))
		{
			if (isBPartnerFlatDiscount())
				return BPartnerFlatDiscount;
			return getFlatDiscount();
		}
		//	Not supported
		else if (DISCOUNTTYPE_Pricelist.equals(getDiscountType())
					|| DISCOUNTTYPE_Formula.equals(getDiscountType())) {
			if (log.isLoggable(Level.INFO)) log.info ("Not supported (yet) DiscountType=" + getDiscountType());
			return Env.ZERO;
		}
		
		//	Price Breaks
		MDiscountSchemaBreak[] brs = getBreaks(false);
		BigDecimal Amt = Price.multiply(Qty);
		if (isQuantityBased()) {
			if (log.isLoggable(Level.FINER)) log.finer("Qty=" + Qty + ",M_Product_ID=" + M_Product_ID + ",M_Product_Category_ID=" + M_Product_Category_ID 
					+ ", M_Warehouse_ID=" + M_Warehouse_ID + ", M_PriceList_ID=" + M_PriceList_ID);
		} else {
			if (log.isLoggable(Level.FINER)) log.finer("Amt=" + Amt + ",M_Product_ID=" + M_Product_ID + ",M_Product_Category_ID=" + M_Product_Category_ID
					+ ", M_Warehouse_ID=" + M_Warehouse_ID + ", M_PriceList_ID=" + M_PriceList_ID);
		}
		BigDecimal discountFormula = Env.ONEHUNDRED;
		for (MDiscountSchemaBreak dbr : brs) {
			IVS_MDiscountSchemaBreak br = new IVS_MDiscountSchemaBreak(dbr);
			if (!br.isActive())
				continue;
			
			if (isQuantityBased()) {
				if (!br.applies(Qty, M_Product_ID, M_Product_Category_ID, M_Warehouse_ID, M_PriceList_ID)) {
					if (log.isLoggable(Level.FINER)) log.finer("No: " + br);
					continue;
				}
				if (log.isLoggable(Level.FINER)) log.finer("Yes: " + br);
			} else {
				if (!br.applies(Amt, M_Product_ID, M_Product_Category_ID, M_Warehouse_ID, M_PriceList_ID)) {
					if (log.isLoggable(Level.FINER)) log.finer("No: " + br);
					continue;
				}
				if (log.isLoggable(Level.FINER)) log.finer("Yes: " + br);
			}
			
			//	Line applies
			BigDecimal discount = null;
			if (br.isBPartnerFlatDiscount())
				discount = BPartnerFlatDiscount;
			else
				discount = br.getBreakDiscount();
			if (log.isLoggable(Level.FINE)) log.fine("Discount=>" + discount);
			
			if(DISCOUNTTYPE_Cascade.equals(getDiscountType())) {
				discountFormula = discountFormula.divide(Env.ONEHUNDRED)
						.multiply(Env.ONEHUNDRED.subtract(discount));
			} else
				return discount;
		}	//	for all breaks
		
		if(DISCOUNTTYPE_Cascade.equals(getDiscountType()) && discountFormula.signum() > 0)
			return Env.ONEHUNDRED.subtract(discountFormula);
		
		return Env.ZERO;
	}	//	calculateDiscount
	
	/**
	 * 	Get fix discounted price
	 *	@param Qty quantity
	 *	@param Price price
	 *	@param M_Product_ID product
	 *	@param M_Product_Category_ID category
	 *	@return fix discounted price or zero
	 */
	private BigDecimal calculateFixedPrice (BigDecimal Qty, BigDecimal Price, int M_Product_ID, int M_Product_Category_ID,
			BigDecimal BPartnerFlatDiscount, int M_Warehouse_ID, int M_PriceList_ID) {
		if (DISCOUNTTYPE_FlatPercent.equals(getDiscountType()) || DISCOUNTTYPE_Formula.equals(getDiscountType())
			|| DISCOUNTTYPE_Pricelist.equals(getDiscountType())) {
			return null;
		}
		
		//	Price Breaks
		MDiscountSchemaBreak[] breaks = getBreaks(false);
		BigDecimal Amt = Price.multiply(Qty);
		for (MDiscountSchemaBreak brs : breaks) {
			IVS_MDiscountSchemaBreak br = new IVS_MDiscountSchemaBreak(brs);
			if (!br.isActive())
				continue;
			
			if (isQuantityBased()) {
				if (!br.applies(Qty, M_Product_ID, M_Product_Category_ID, M_Warehouse_ID, M_PriceList_ID))
					continue;
			} else {
				if (!br.applies(Amt, M_Product_ID, M_Product_Category_ID, M_Warehouse_ID, M_PriceList_ID))
					continue;
			}
			
			//	Line applies
			if (!br.isBPartnerFlatDiscount())
			{
				if (br.getFixedPrice() != null && br.getFixedPrice().signum() > 0)
				{
					return br.getFixedPrice();
				}
			}
			return null;
		}	//	for all breaks
		
		return null;
	}	//	calculateDiscount

}
