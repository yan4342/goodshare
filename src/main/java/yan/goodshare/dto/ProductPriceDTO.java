package yan.goodshare.dto;

import java.math.BigDecimal;

public class ProductPriceDTO {
    private String platform;
    private String name;
    private BigDecimal price;
    private String imageUrl;
    private String productUrl;
    private String shopName;

    public ProductPriceDTO() {
    }

    public ProductPriceDTO(String platform, String name, BigDecimal price, String imageUrl, String productUrl, String shopName) {
        this.platform = platform;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.productUrl = productUrl;
        this.shopName = shopName;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public void setProductUrl(String productUrl) {
        this.productUrl = productUrl;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }
}
