const axios = require('axios');
const { faker } = require('@faker-js/faker');

const API_BASE_URL = 'http://localhost:8080/api';

const USERS_TO_CREATE = 5;
const POSTS_PER_USER = 10;

const TAGS = ['家居', '数码', '玩具', '服装', '美食'];

const POST_TEMPLATES = [
  {
    title: '我的温馨小窝',
    content: '刚刚布置好的客厅，阳光洒进来太舒服了！',
    tags: ['家居']
  },
  {
    title: '新入手的好物分享',
    content: '这个新款耳机音质真的绝了，降噪效果满分！',
    tags: ['数码']
  },
  {
    title: '童心未泯',
    content: '终于集齐了这套绝版模型，太开心了！',
    tags: ['玩具']
  },
  {
    title: '今日穿搭 OOTD',
    content: '这件新买的连衣裙也太适合夏天了吧！',
    tags: ['服装']
  },
  {
    title: '周末探店',
    content: '发现一家超棒的日料店，刺身很新鲜，天妇罗也炸得刚刚好。',
    tags: ['美食']
  },
  {
    title: '厨房新宠',
    content: '这个多功能料理机真的太方便了，榨汁、绞肉、和面都能搞定。',
    tags: ['美食', '家居']
  },
  {
    title: '我的桌面改造计划',
    content: '换了新的机械键盘和显示器，写代码都更有动力了。',
    tags: ['数码']
  }
];

const getRandomElement = (arr) => arr[Math.floor(Math.random() * arr.length)];

const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

async function registerUser(userData) {
  try {
    await axios.post(`${API_BASE_URL}/auth/register`, userData);
    console.log(`User ${userData.username} registered successfully.`);
    return true;
  } catch (error) {
    console.error(`Failed to register user ${userData.username}:`, error.response ? error.response.data : error.message);
    return false;
  }
}

async function loginUser(credentials) {
  try {
    const response = await axios.post(`${API_BASE_URL}/auth/login`, credentials);
    console.log(`User ${credentials.username} logged in successfully.`);
    return response.data.data.token;
  } catch (error) {
    console.error(`Failed to login user ${credentials.username}:`, error.response ? error.response.data : error.message);
    return null;
  }
}

async function createPost(postData, token) {
  try {
    await axios.post(`${API_BASE_URL}/posts`, postData, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    console.log(`Post "${postData.title}" created successfully.`);
  } catch (error) {
    console.error(`Failed to create post "${postData.title}":`, error.response ? error.response.data : error.message);
  }
}

async function main() {
  console.log('Starting data seeding process...');

  for (let i = 0; i < USERS_TO_CREATE; i++) {
    const username = faker.internet.userName();
    const email = faker.internet.email();
    const password = 'password123';

    const registered = await registerUser({ username, email, password, captcha: '123456' });
    if (!registered) {
      continue;
    }

    await sleep(1000); // Wait a bit before logging in

    const token = await loginUser({ username, password });
    if (!token) {
      continue;
    }

    for (let j = 0; j < POSTS_PER_USER; j++) {
      const template = getRandomElement(POST_TEMPLATES);
      const post = {
        title: `${template.title} #${j + 1}`,
        content: `${template.content} ${faker.lorem.paragraph()}`,
        tags: template.tags.concat(getRandomElement(TAGS)).filter((v, i, a) => a.indexOf(v) === i), // Add a random tag and remove duplicates
        coverUrl: faker.image.urlLoremFlickr({ category: 'abstract' }),
        imageUrls: [faker.image.urlLoremFlickr({ category: 'technics' })]
      };

      await createPost(post, token);
      await sleep(500); // Add a small delay between posts
    }
  }

  console.log('Data seeding process finished.');
}

main();
