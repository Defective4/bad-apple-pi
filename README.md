# Bad Apple!! on a 16x2 LCD Display  
[![YouTube Video Views](https://img.shields.io/youtube/views/lI8jfwCPBJM)](https://www.youtube.com/watch?v=lI8jfwCPBJM&feature=youtu.be)

## How to run
### Requirements  
Hardware:
- A Raspberry Pi device (tested on 3B)
- A HD44780 with a I2C converter

Software:
- Git
- Maven
- Java 17+

### Running
1. Clone the repository
```bash
git clone github.com/defective4/bad-apple-pi.git
```
2. Run the code
```bash
cd bad-apple-pi
sudo mvn compile exec:java
```
Note that you need to use `sudo`!