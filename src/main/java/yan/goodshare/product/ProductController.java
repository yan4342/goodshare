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

    @PostMapping("/posts/{postId}/products")
    public ResponseEntity<Product> addProductToPost(@PathVariable Long postId, @RequestBody Product product) {
        return ResponseEntity.ok(productService.addProductToPost(postId, product));
    }

    @PostMapping("/products/{productId}/prices")
    public ResponseEntity<Price> addPriceToProduct(@PathVariable Long productId, @RequestBody Price price) {
        return ResponseEntity.ok(productService.addPriceToProduct(productId, price));
    }
}
