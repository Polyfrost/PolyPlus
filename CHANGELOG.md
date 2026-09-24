## Unreleased changes
- Fixed messages containing emoji not being compacted by Chat Patches and Chat Tweaks
- Fixed cancelling Microsoft sign-in showing a cancellation error
- Fixed cover images that were still loading when leaving a screen never loading again until restart
- Fixed world hosting and joining through Poly+ no longer working after about an hour or a brief connection drop until the game was restarted
- Fixed Poly+ giving up on reconnecting after being offline for more than a few minutes
- Fixed hosting a world for friends silently doing nothing when Poly+ couldn't start the session, such as while offline. The error is now shown instead.
- Fixed friends sometimes failing to join a world you're hosting through Poly+ and timing out instead
- Fixed a Poly+ hosted world connection carrying on after a packet was lost, which could desync players or kick them with confusing errors. They are now disconnected with the actual reason.
- Fixed players without Poly+ being unable to join worlds hosted by a Poly+ user, such as through e4mc or Essential
