package yan.goodshare.crawler.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductHistoryDTO implements Serializable {
    private String date; // Format: YYYY-MM-DD
    private double minPrice;
    private double avgPrice;
    private String note;
}
