/**
 * 
 */
package dev.vsuarez.component;

import org.adempiere.base.AbstractProductPricing;
import org.adempiere.base.IProductPricingFactory;

import dev.vsuarez.model.IVS_MProductPricing;

/**
 * @author <a href="mailto:victor.suarez.is@gmail.com">Ing. Victor Suarez</a>
 *
 */
public class ProductPricingFactory implements IProductPricingFactory {

	@Override
	public AbstractProductPricing newProductPricingInstance() {
		return new IVS_MProductPricing();
	}

}
