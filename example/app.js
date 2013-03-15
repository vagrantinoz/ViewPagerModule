// This is a test harness for your module
// You should do something interesting in this harness 
// to test out the module and to provide instructions 
// to users on how to use it by example.


function dpToPx(dp) {
	return dp * Ti.Platform.displayCaps.platformWidth / Ti.Platform.displayCaps.dpi;
}

// TODO: write your module tests here
var pager = require('com.oxgcp.viewPager');
Ti.API.info("module is => " + pager);

var win = Ti.UI.createWindow({
	backgroundColor: "#fff",
});

function createView() {
	
	var _reverse = false;
	
	var view = Ti.UI.createView({
		backgroundColor:'#fff',
	})
	
	var child1 = Ti.UI.createView({
		backgroundColor: "#246",
		width: "100dp",
		height: "50dp",
	})
	view.add(child1);
	
	var child2 = Ti.UI.createView({
		backgroundColor: "#48b",
		width: "150dp",
		height: "75dp",
	})
	
	view.addEventListener('singletap', function() {
		
		view.animate({
			transform: Ti.UI.create2DMatrix().scale(1, 1, 0, 1),
			duration: 300,
		}, function() {
			if (!_reverse) {
				view.add(child2);
				view.remove(child1);
			}
			else {
				view.add(child1);
				view.remove(child2);
			}
			_reverse = !_reverse;
			
			view.animate({
				transform: Ti.UI.create2DMatrix().scale(0, 1, 1, 1),
				duration: 300,
			});
		});
	});
	
	return view;
}

var views = [];

for(var i=0; i<5; i++){
	views.push(createView());
}



var scrollableView = pager.createPagerView({
	width: Ti.UI.FILL,
	backgroundColor: '#999',
	contentWidth: dpToPx(200),
	contentHeight: dpToPx(100),
  views:views,
});

scrollableView.setPageMargin(dpToPx(15));
scrollableView.setOffscreenPageLimit(3);
scrollableView.setClipChildren(false);
scrollableView.setHorizontalFadingEdgeEnabled(false);
scrollableView.setOverScrollMode(false);
// scrollableView.setContentWidth(dpToPx(200));
// scrollableView.setContentHeight(dpToPx(100));

win.add(scrollableView);

win.open();