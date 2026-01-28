package yan.goodshare.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductHistoryDTO implements Serializable {
    private String date;
    private BigDecimal minPrice;
    private BigDecimal avgPrice;
}
