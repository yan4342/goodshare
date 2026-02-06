package yan.goodshare.product;

import yan.goodshare.entity.Price;
import yan.goodshare.entity.Product;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // 添加商品到帖子
    @PostMapping("/posts/{postId}/products")
    public ResponseEntity<Product> addProductToPost(@PathVariable Long postId, @RequestBody Product product) {
        return ResponseEntity.ok(productService.addProductToPost(postId, product));
    }

    // 添加商品价格
    @PostMapping("/products/{productId}/prices")
    public ResponseEntity<Price> addPriceToProduct(@PathVariable Long productId, @RequestBody Price price) {
        return ResponseEntity.ok(productService.addPriceToProduct(productId, price));
    }
}
