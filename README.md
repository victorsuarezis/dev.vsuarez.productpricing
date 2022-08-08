# iDempiere Product Price Extension

For managing Prices, Price Lists, profit margins, and discounts, the system uses an Interface that allows you to extend and customize this functionality, which is **IProductPricing**. This functionality is implemented and extended in this plugin

## The calculation of prices is used through Callouts, Processes, and Model from:

- Order Lines
- Invoice Lines
- Requisition Lines
- Return Authorization Lines
- Project Lines

With this extension, you can modify the price calculation, without having to go directly to each document

## Improvement made to Discounts:

### With this extension the following functionality was added to the iDempiere Discounts module:

- Make different discounts according to the Warehouse and the Price List of a document
- It is currently implemented for Orders and Invoices, where it was required
- The Warehouse of the Order is taken, in the case of the Invoice, the Warehouse of the order associated with the Line is taken, if it does not have it, no discount is made for Warehouse in this case, and it takes the normal behavior.

## Setting

For this improvement, the Discount Scheme of the system is used and extended. Which works as follows:

- Each Business Partner or Business Partner Group has a Discount Scheme selected. If the Business Partner has not selected any scheme, that of the Group is used, if in none of the 2 options there is any scheme selected, no discounts are made for this Business Partner.

- There are 2 Types of Discounts, which are selected in the Header of the Discount Scheme:

  - **Flat Percent**: Allows you to place a % directly in this window, and all Business Partner or Business Partner Group that have this scheme, will have this discount, or by checking the "B.Partner Flat Discount", you will take the Flat Discount % established for each Business Partner directly in your tab.

  - **Breaks**: The *Discounts tab* is displayed, where different discounts are established according to the following characteristics of the Document:

    - Product

    - Product Category

    - Warehouse *

    - Product Pricing * 

      These last aggregates for in this development

    - In this "Discounts" tab you can have several lines, prioritized by a sequence. When calculating the Discount on the Line of a Document, these discounts are run in the order indicated in this sequence, and when one of them coincides with its characteristics, the discount configured here is taken, if the Check "B.Partner Flat Discount" is marked, the % established in the Business Partner record is taken.

      - In the *Break Value* field, we can establish a minimum amount, to use this discount
      
## Example
Discount Scheme Header
![Screenshot_20220808_111118](https://user-images.githubusercontent.com/13411451/183451385-6d75efa0-99d1-4c16-b4a8-c01a19b77d74.png)

### Discount Settings
- The first discount that we see here is the first to be evaluated, if the document is from the Furniture Warehouse and has a Standard Price List, then the discount for this case will be 10%.

![Screenshot_20220808_111127](https://user-images.githubusercontent.com/13411451/183451669-cd56bd99-ee5d-4afb-92e8-4804306ad86c.png)

- In this second discount, we see that there is no parameter selected, this means that for all the documents of the Third Parties or Groups that have this Discount Scheme, regardless of their Product, Product Category, Warehouse, or Price List, will take this discount

![Screenshot_20220808_111135](https://user-images.githubusercontent.com/13411451/183452110-2dd26fba-879f-4924-b7e1-bff28c249ede.png)

- In this previous example, we can see that when calculating the discount, if the Document coincides with the First case, in the second image, it will have a 10% discount, otherwise, then the discount will be 5%, which is applied for all other documents, which do not coincide with this Warehouse and Price List.

