package com.example.demo.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.AuctionRepository;
import com.example.demo.Repository.AutoBidConfigRepository;
import com.example.demo.Repository.BidRepository;
import com.example.demo.entity.Auction;
import com.example.demo.entity.Bid;
import com.example.demo.entity.Users;
import com.example.demo.statusEnum.AuctionStatus;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuctionService {
	
	@Autowired
	private AuctionRepository auctionRepository;
	@Autowired
	private BidRepository bidRepository;
	
	@Autowired
	private BidService bidService;
	
	@Autowired
	private EmailService emailService;

	@Autowired
	private AutoBidConfigRepository autoBidRepo;
	LocalDateTime NOW =ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
	public List<Auction> getAllAuctions() {
		List<Auction> auctions = auctionRepository.findAll();
		return auctions;
	}

	public List<Auction> getUpcomingAuctions() {
		LocalDateTime NOW = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
		List<Auction> auctions = auctionRepository.findAll();
		List<Auction> upcomingAuctions = auctions.stream()
				.filter(auction -> auction.getStartDate().isAfter(NOW))
				.toList();
		
		return upcomingAuctions;
	}

	public Auction getAuctionById(Integer auctionId) {
		
		Auction auction = auctionRepository.findById(auctionId).orElse(null);
		if (auction != null) {
			return auction;
		}
		
		
		return null;
	}

	public List<Auction> getAuctionByUserId(Users user) {
	    List<Bid> bids = bidRepository.findAllByUser(user);

	    List<Auction> auctions = bids.stream()
	            .map(Bid::getAuction)
	            .distinct()
	            .toList(); // requires Java 16+, use collect(Collectors.toList()) for earlier versions

	    return auctions;
	}


	public Auction saveAuction(Auction auction) {
		if (auction != null) {
			return auctionRepository.save(auction);
		}
		
		return null;
	}



	public List<Auction> getWonAuctionByUserId(Users user) {
		// find all actions won by a user from acution table
		List<Auction> auctions = auctionRepository.findAuctionsWonByUser(user.getId());
		return auctions;
	}
	// based on start and end date time update auction status
	public void  updateAuctionStatus() {
		LocalDateTime NOW = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
		List<Auction> auctions = auctionRepository.findAll();
		bidService.updateBidStatusAndTime();
		// dont update auction status if statuc is CANCELED AND COMPLETED
		if (auctions != null) {
		for (Auction auction : auctions) {
			if (auction.getStatus() != AuctionStatus.CANCELED && auction.getStatus() != AuctionStatus.COMPLETED) {
				
				if (auction.getStartDate().isBefore(NOW)
						&& auction.getEndDate().isAfter(NOW) && auction.getStatus() != AuctionStatus.ACTIVE) {
					auction.setStatus(AuctionStatus.ACTIVE);
					auctionRepository.save(auction);
				} else if (auction.getEndDate().isBefore(NOW) && auction.getStatus() != AuctionStatus.COMPLETED)  {
					 finalizeCompletedAuction(auction);
					auctionRepository.save(auction);
				} else if (auction.getStartDate().isAfter(NOW) && auction.getStatus() != AuctionStatus.UPCOMING) {
					auction.setStatus(AuctionStatus.UPCOMING);
					auctionRepository.save(auction);
				}
				
			}
		
		}
		}
	}
	// update auction end time  plus mins
	public Auction updateAuctionEndTime(Integer auctionId, int secondsToAdd) {
		log.info("actuion  " + auctionId +  " " + secondsToAdd);
		    if (auctionId != null) {
		        Auction auction = auctionRepository.findById(auctionId).orElse(null);
		        
		        if (auction != null) {
		        	
		            auction.setEndDate(auction.getEndDate().plusSeconds(secondsToAdd));
		            log.info( " "  + auction);
		            return auctionRepository.save(auction);
		        }
		    }
		   log.info("auction not updated");
		    return null;
		
	}

	public List<Auction> getAllAuctionIncludingInActive() {
			log.info("getAllAuctionIncludingInActive at service");
		return	auctionRepository.findAllIncludingInactive();
		
	}
	
	 public boolean canDeleteAuction(Integer auctionId) {
	        return !bidRepository.existsByAuctionId(auctionId)
	                && !autoBidRepo.existsByAuctionId(auctionId);
	    }

	    public boolean softDeleteAuction(Integer auctionId) {
	        Optional<Auction> optionalAuction = auctionRepository.findById(auctionId);
	        if (optionalAuction.isPresent()) {
	            Auction auction = optionalAuction.get();
	            auction.setActive(0);
	            auctionRepository.save(auction);
	            return true;
	        }
	        return false;
	    }
	
	
	    public boolean recoverAuction(Integer auctionId) {
	        Auction auction = auctionRepository.findByIdIncludingInactive(auctionId);
	        if (auction == null || auction.getActive() == 1) {
	            return false;
	        }

	        auction.setActive(1);
	        auctionRepository.save(auction);
	        return true;
	    }

	    public void finalizeCompletedAuction(Auction auction) {
	        List<Bid> bids = bidService.getBidsByAuction(auction);

	        if (bids != null && !bids.isEmpty()) {
	            // Find highest bid
	            Bid highestBid = bids.stream()
	                    .max(Comparator.comparingDouble(Bid::getBidAmount))
	                    .orElse(null);

	            if (highestBid != null) {
	                auction.setHighestBidAmount(highestBid.getBidAmount());
	                auction.setHighestBidderId(highestBid.getUser().getId());
	                auction.setBidId(highestBid.getId());
	                auction.setStatus(AuctionStatus.COMPLETED);
	                auctionRepository.save(auction);

	                // Accept highest bid, reject others
	                for (Bid bid : bids) {
	                    if (bid.getId().equals(highestBid.getId())) {
	                        bid.setBidStatus("ACCEPTED");

	                        // ✅ Send email to winning user
	                        String email = bid.getUser().getEmail();
	                        String subject = "Congratulations! Your Bid Was Accepted";
	                        String body = "Dear " + bid.getUser().getUsername() + ",\n\n" +
	                                      "Congratulations! Your bid of ₹" + bid.getBidAmount() + 
	                                      " has been accepted for the auction item \"" + auction.getName() + "\".\n\n" +
	                                      "Thank you for participating.\n\nRegards,\nAuction Team";

	                        emailService.sendMail(email, subject, body);
	                    } 
	                    // You can optionally reject other bids here:
	                    // else {
	                    //     bid.setBidStatus("REJECTED");
	                    // }
	                }
	                bidService.saveAllBids(bids);
	            }
	        } else {
	            // No bids placed, just mark as completed
	            auction.setStatus(AuctionStatus.COMPLETED);
	            auctionRepository.save(auction);
	        }
	    }

	
}
 