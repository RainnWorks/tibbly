# Raw user instructions — captured verbatim

This file preserves what the user actually said, lightly grouped but not edited.
It is the source-of-truth when downstream docs become abstracted away from the
original intent. Re-read whenever something feels off-mission.

---

## Block 1 — The big pivot (productize, monorepo, paid SaaS)

> Right. We have now proven that this is what we want to build. Like the MVP works. I want us to go to the next level now and start to think about how we can productize this.

> Fundamentally, we have built out an application that works as an MCP server locally. However, I want this to be now driven by the backend and to be a paid service.

> This means that we need to basically convert this repository into a monorepo with a proper backend, a proper landing page, maybe even a backend system that people can log into and see stats and things for the future. I don't want us to go too hard on that right now, but fundamentally we do need to reorganize this repo into a monorepo.

> With that in mind, I want us to now go really deep on this.

## Block 2 — Backend, DB, LLM provider

> I have added a `.env` file in the root with an open router API key. This is what the backend is going to be driven by.

> Now, given what we're trying to achieve, now that this is going to be a product, we are going to be having monthly paying customers. They're going to use a certain amount of tokens which we'll want to be storing within a database.

> I think locally we can use PG Lite as the database. And when we sort of deploy this, we'll be using a proper production Postgres database.

> Open router will be the thing driving the API and the model, which means no need for `claude` etc. locally.

## Block 3 — How the plugin talks to the backend

> I want us to think about how we do the whole connection to the backend from the runlite client, enable this chat within their client, and have access to their tools while they're chatting, because these tools must live and are connected via the API when the runlite client is started, etc.

## Block 4 — Write this all up, then expand, then build

> Now, write this all up as we're going. I want us to do an overnight pass on transforming this into a full product.

> What I mean by this is your first job will be to write it up. Your second job will be to scrutinize what you've written up and what I've told you and expand it as much as you can with enough documentation and ideas on where we're headed and why we would go in that direction.

> Once you have that, you're going to use this spec and this design document, etc.

## Block 5 — Scope: full product (frontend, plugin visuals, marketing, backend, auth)

> I want to be clear that this needs to include frontend. This needs to include the runlite client concepts and you know visuals. This needs to include marketing web pages. It needs to include backend that you can log into. We need to get in authentication.

## Block 6 — Authentication via RuneLite identity (frictionless, multiple OSRS accounts)

> Side note here that authentication will be driven by the runlite client — using them being logged in as proof somehow. I don't know if it's 100% feasible, but I would really like this to be super frictionless. However, obviously they will need to be tying credentials to access our APIs.

> I'm thinking that they tie that to one or multiple accounts.

> I don't know if there's any way for RuneLite to provide this kind of 100% proof that that user is logged into that account. If there's any kind of special secret key or private or public key style thing we can get from the user being logged in in RuneLite. I would like us to think maybe very briefly about what that backend would look like.

## Block 7 — Stripe and the "why pay" thesis

> Maybe even look at getting Stripe involved. Most likely we will need Stripe to take payment.

> I want you to think deeply about why a user would pay for this. I've given you a lot of context so far and I've built it for like my needs. But I want you to think about it and sort of make sure that the marketing pages have that in mind.

## Block 8 — Token economy / margin

> We need to start thinking about, well, if we're productising, how do we limit the amount of context for tools, etc. so that our back end isn't overwhelmed by 10,000 plus tokens just for loading the tools that is available to it for every single chat, right?

> Because obviously we've we're here to make money on the monthly subscription with them using ideally the least amount of tokens as possible.

## Block 9 — Sub-agents (5-10), manage them like a product team

> Again, once you have this full spec, your next job will be to take a step back and create agents around this. And I'm thinking maybe five to ten sub agents that you are going to be managing.

> The idea behind you managing these agents in this way is to reduce the amount of context that you're managing at any given time. You spin off to sub agents, they complete tasks. That way you will keep as much of the context flow out of your own context as you go.

> Treat this like running a team, a product team, and go and do research on what best makes up sub agents and how people are doing these sorts of things.

## Block 10 — Test everything (Claude in Chrome)

> One thing I want to note here very hard is that at every single point while you test, you are, or while you're building, you are testing, testing everything you can. If you are unable to test it, you will try and find any other way you can. And if you really, really cannot test it, which I imagine will just be the post-logged in stuff for the runlite client, everything else you should fundamentally be able to test.

> You have access to Claude in Chrome, make use of it.

## Block 11 — Tech stack

> For the backend, specifically sort of the login backend, the app backend, I want you to use bun and react and tailwind.

## Block 12 — Frontend taste (the "AI taste" / "LLM taste" skill)

> I also want you to go and look up skills that were but that are potential use. In particular, there's one that's something like LLM taste or AI taste that you should go and look up and understand how it's used.

> Be careful when searching. Obviously, we want to keep you from reading something that could potentially inject anything negative.

> But the whole point of this taste LLM skill is for you to get an idea of what the front end could look like.

## Block 13 — Full reign, autonomous

> You have full reign on this. Fundamentally, I will not be available for the next six to eight hours.

> The rules here are that you are going to loop every 2 minutes to ensure that all of your agents are running. At no point should you ever stop and say, "hey, I'm good to go, we'll wait for Tom to come back."

> You cannot ask me any questions because I will not be here.

> Your job is to make sure that the agents are running, test absolutely thoroughly whatever you can test, get agents writing tests, get agents writing the back end, get agents writing and thinking deeply about what the token usage and tool usage, etc. can look like.

## Block 14 — Real-time stats / network / "live agents" angle

> We'll also need to think about real time statistics. I do see a really cool thing being a kind of network approach, and I want you to have a specific agent focusing on we have all of these runlite agents connected.

> How can we use them together, even if it's just for the backend side of things for fun and for marketing purposes, for like live clients connected, etc. We should be thinking about th[...] (cut off mid-sentence)

## Block 15 — OSRS Wiki imagery (marketing visual language)

> Think about how you would best showcase how this works. I'm thinking animations using maybe — for now we just use the rune wiki, you know, images, etc., they have of equipment and things like that.

> Like go to the rune like wiki, make sure you're using the runescape with the OSRS wiki as much as possible for any kind of context and design elements, etc.

> I would say I don't necessarily like the overall design, but they do have a very deep library of graphics that we should definitely be pulling in as much of as possible to make this feel like a RuneScape native website.

## Block 16 — Never stop, never pause (durable directive)

> I'm gonna stress this once more. At no point should you ever stop working and pause. And I want you to write that up in `claw.md` (CLAUDE.md) and everywhere else you can. You do not pause, you do not stop. You will work all night. Your agents will work all night and keep working.

> And if there's anything in our previous chat that you need to be writing up now so that you have extra context and extra work to focus on post, you should do that.

## Block 17 — Add an agent for RuneLite API depth

> One of the agents should be looking and going deep on the RuneLite surface, specifically the API surface and things that we have access to that maybe we've missed that we could include.

## Block 18 — Think about memory systems FIRST

> You will need to think deeply, and I want you to do this first before you do anything else. You will need to think deeply about how to create memories and what memory systems you should be using to ensure that autonomous mode is fully running and doesn't lose context as you go.

> What we cannot have is an autonomous mode which slowly spirals into things that were not asked for because of hallucinations, etc. Make sure that you're bringing it back to the main focus every time.

## Block 19 — Write everything up, then tidy, then go

> The first thing I want you to do right now is write up a document with everything I've said to you. You know, nicely break it down, but don't edit too much.

> Once you've done that, you can then go and tidy up the points and edit that document into sort of more of a structured content and how you're going to approach it.

> And then I'll just want you to go. I want you to consider me not here. You cannot ask me questions. You are going fully autonomous. I will add my notes as I go.

> Fundamentally, you are up, you and your team. They do not stop, they run forever.

## Block 20 — Add a community/user research agent

> I want an agent thinking deeply about runlite users and runescape users where the community is heading, doing research in how to out where this AI tool could help solve potential problems within the entire aspect. And this will just be used to inform the rest of the different parts that need building.

## Block 21 — Research as a living, indexed system that informs other agents

> An agent doing this kind of research doesn't have to stop at runlite, but we should have research agents always providing context to all of the other sub agents that are around. This research topic should have its own folder within this monorepo and it should be further growing and used as elements. And obviously that agent will need to think deeply about how to structure that folder and where the documentation lives.

> I think it's really important that we use `claud.md` (CLAUDE.md) as a living architecture. So the idea being that we create a kind of documentation route which all of the agents have access to and can read from when necessary or when it might help. And they'll do that by reading the `claud.md` or another sort of base documentation folder that will have a bunch of it's basically like an index for all the documentation and research and things that we've pulled in so far.

## Block 22 — Memory system for autonomous, looping agents (with future use cases)

> One agent should be researching how we do the memory system locally, how we can best sort of serve an autonomous and looping agent system within this repo. The intention being that right now it's purely focused on our conversation, but in the future it will be used to drive things like the GitHub issues, for example.

## Block 23 — Documentation discipline; research never wasted

> Hence the need to have proper documentation and properly written up system on what an agent running in this system can use and making sure that any research that you do never goes to waste because it is written up and automatically becomes part of this memory system.

## Block 24 — Memory system is an ADDITION; the three real deliverables

> I will highlight that this memory system is an addition. The goal must be that by tomorrow we — I can wake up and we have delivered the three things I asked for, which is:
> 1. a productized system for this LLM API client backed by open router
> 2. a potentially very nice and somewhat live somehow front page marketing page
> 3. a backend system for them to log in and add credits, etc.

> If we decide that the login isn't necessary, that was probably even better, right? But fundamentally we should support whatever is necessary to make sure that we can apply tokens securely, their payments securely against their token usage, etc.

> We'll also need to think about token tracking and everything like that.

## Block 25 — Gaps analysis agent + loop cadence is 20 minutes

> I have spoken to you about a lot so far, and I think it's very well worth you doing a full gaps analysis as well. And maybe that's another agent — a gaps analysis that again forms part of that continuous loop until tomorrow.

> I want you to start the loop right now, every 20 minutes, wake yourself up and ensure that all sub agents are moving towards their goals.

---

## Earlier (this session, before the productize pivot)

A great deal was already discussed about the plugin itself. Quick summary so
agents have it without me copying every transcript:

- We added persistent tile markers backed by a sidebar panel.
- We added a unified `ManagedVisualsRegistry` so any agent-added visual
  (bank tab, tile marker, NPC / object / ground-item / inventory-item highlight)
  appears in a sidebar with a ✕ button.
- We added integrations with sibling RuneLite plugins — XP Tracker, Slayer,
  Clue Scrolls, Party — and pre-fetch their data into the chat preamble.
- We added smart visual defaults that READ the sibling plugin's color config
  so highlights match what the user already sees.
- We added a Notifier wrapper, a ChatMessageManager wrapper, a `::ai` chat
  command, agility shortcut catalog, fishing spot catalog, item mapping lookup,
  user-marker / object-marker / npc-indicator / notes plugin readers, an NPC
  max-HP service.
- We finished at 72 unique MCP tools (~8K tokens of tool surface per chat).
- We pushed to `git@github.com:RainnWorks/osrs-llm-helper.git` (private).

That body of work is the launching pad. The pivot is: keep all of it inside
`apps/plugin/`, build the rest around it.
