package yan.goodshare.tag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import yan.goodshare.mapper.TagMapper;
import yan.goodshare.entity.Tag;

import java.util.List;

@Service
public class TagService {

    private final TagMapper tagMapper;

    public TagService(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    public Tag createTag(String name) {
        Tag existingTag = tagMapper.selectOne(new QueryWrapper<Tag>().eq("name", name));
        if (existingTag != null) {
            throw new RuntimeException("Tag already exists");
        }
        Tag tag = new Tag();
        tag.setName(name);
        tagMapper.insert(tag);
        return tag;
    }

    public List<Tag> getAllTags() {
        return tagMapper.selectList(null);
    }

    public void deleteTag(Long id) {
        tagMapper.deleteById(id);
    }
}
