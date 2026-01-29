package yan.goodshare.crawler.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductPriceDTO implements Serializable {
    private String platform;
    private String title;
    private double price;
    private String url;
    private String imageUrl;
    private String shopName;
}
