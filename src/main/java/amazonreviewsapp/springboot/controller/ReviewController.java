package amazonreviewsapp.springboot.controller;

import amazonreviewsapp.springboot.dto.EditReviewRequestDto;
import amazonreviewsapp.springboot.dto.MostUsedWordResponseDto;
import amazonreviewsapp.springboot.dto.ReviewRequestDto;
import amazonreviewsapp.springboot.dto.ReviewResponseDto;
import amazonreviewsapp.springboot.jwt.JwtTokenProvider;
import amazonreviewsapp.springboot.mapper.ReviewMapper;
import amazonreviewsapp.springboot.model.Review;
import amazonreviewsapp.springboot.model.User;
import amazonreviewsapp.springboot.service.ReviewService;
import amazonreviewsapp.springboot.service.UserService;
import java.security.InvalidKeyException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reviews")
public class ReviewController {
    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewMapper mapper;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenProvider provider;

    @GetMapping
    @RequestMapping("/most-used-words")
    public List<MostUsedWordResponseDto> findMostUsedWords(@RequestParam(defaultValue = "1000") String limit) {
        List<MostUsedWordResponseDto> words = reviewService.findMostUsedWordsFromReviews();
        return words.stream()
                .limit(Long.parseLong(limit))
                .collect(Collectors.toList());
    }

    @PostMapping
    @RequestMapping("/")
    public ReviewResponseDto addNewReview(@RequestBody ReviewRequestDto reviewRequestDto,
                                          HttpServletRequest request) {
        User user = getUserFromRequest(request);
        Set<Review> reviews = user.getUserReviews();
        Review toAdd = mapper.getReviewFromReviewRequestDto(reviewRequestDto);
        reviewService.save(toAdd);
        reviews.add(toAdd);
        user.setUserReviews(reviews);
        userService.save(user);
        return mapper.getReviewResponseDtoFromReview(toAdd);
    }

    @PutMapping
    @RequestMapping("/current-review/")
    public ReviewResponseDto editReview(@RequestBody EditReviewRequestDto reviewRequestDto,
                                        HttpServletRequest request) throws InvalidKeyException {
        User user = getUserFromRequest(request);
        Set<Review> reviews = user.getUserReviews();
        int numb = (int) reviews.stream()
                .filter(review -> review.getReviewId().equals(reviewRequestDto.getReviewId()))
                .count();
        if (numb != 1) {
            throw new InvalidKeyException("Wrong review id, you can't edit other users' reviews.");
        }
        Review reviewToEdit = reviews.stream()
                .filter(review -> review.getReviewId().equals(reviewRequestDto.getReviewId()))
                .findFirst().get();
        reviews.remove(reviewToEdit);
        mapper.editReviewFromEditReviewRequestDto(reviewRequestDto,reviewToEdit);
        reviewService.save(reviewToEdit);
        reviews.add(reviewToEdit);
        user.setUserReviews(reviews);
        userService.save(user);
        return mapper.getReviewResponseDtoFromReview(reviewToEdit);
    }

    @DeleteMapping
    @RequestMapping("/{id}")
    public boolean deleteReviewById(@PathVariable String id) {
        Optional<Review> review = reviewService.findReviewById(id);
        if (review.isEmpty()) {
            throw new IllegalArgumentException("There is no review with this id");
        }
        Review reviewToDelete = review.get();
        String userId = userService.findUserByReviewId(reviewToDelete.getReviewId());
        User user = userService.findById(userId).get();
        Set<Review> userReviews = user.getUserReviews();
        userReviews.remove(reviewToDelete);
        user.setUserReviews(userReviews);
        userService.save(user);
        reviewService.deleteReviewById(id);
        return true;
    }

    private User getUserFromRequest(HttpServletRequest request) {
        String token = provider.resolveToken(request);
        return userService.findUserByProfileName(provider.getUserNameByToken(token)).get();
    }
}
