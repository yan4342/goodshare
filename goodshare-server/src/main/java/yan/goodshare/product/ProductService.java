package yan.goodshare.product;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import yan.goodshare.mapper.PostMapper;
import yan.goodshare.mapper.PriceMapper;
import yan.goodshare.mapper.ProductMapper;
import yan.goodshare.entity.Post;
import yan.goodshare.entity.Price;
import yan.goodshare.entity.Product;

import java.util.List;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final PriceMapper priceMapper;
    private final PostMapper postMapper;

    public ProductService(ProductMapper productMapper, PriceMapper priceMapper, PostMapper postMapper) {
        this.productMapper = productMapper;
        this.priceMapper = priceMapper;
        this.postMapper = postMapper;
    }

    public Product addProductToPost(Long postId, Product product) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new RuntimeException("Post not found");
        }
        product.setPostId(postId);
        productMapper.insert(product);
        return product;
    }

    public Price addPriceToProduct(Long productId, Price price) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new RuntimeException("Product not found");
        }
        price.setProductId(productId);
        priceMapper.insert(price);
        return price;
    }

    public List<Product> getProductsByPost(Long postId) {
        return productMapper.selectList(new QueryWrapper<Product>().eq("post_id", postId));
    }
}
