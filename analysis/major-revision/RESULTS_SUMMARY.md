# Major-revision result digest

All effects are paired treatment-minus-control estimates. Confidence intervals are
unadjusted 95% Monte Carlo intervals; relative effects use the matched control mean.

## RQ3: recommendation policies at 14.7% direct reach

| Outcome | Policy | Strategy | Control (%) | Effect (pp) | CI low | CI high | Relative (%) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| fake_repost_share_decisions | disabled | combined | 15.575 | 1.182 | 1.077 | 1.287 | 7.588 |
| fake_repost_share_decisions | disabled | credibility | 15.575 | 0.337 | 0.293 | 0.381 | 2.163 |
| fake_repost_share_decisions | disabled | informational | 15.575 | 0.549 | 0.497 | 0.600 | 3.522 |
| fake_repost_share_decisions | discovery | combined | 15.118 | 1.795 | 1.610 | 1.979 | 11.871 |
| fake_repost_share_decisions | discovery | credibility | 15.118 | 0.445 | 0.357 | 0.532 | 2.940 |
| fake_repost_share_decisions | discovery | informational | 15.118 | 0.831 | 0.740 | 0.923 | 5.499 |
| fake_repost_share_decisions | flag_only | combined | 30.400 | 6.457 | 6.177 | 6.737 | 21.239 |
| fake_repost_share_decisions | flag_only | credibility | 30.400 | 1.925 | 1.800 | 2.049 | 6.331 |
| fake_repost_share_decisions | flag_only | informational | 30.400 | 2.667 | 2.508 | 2.826 | 8.773 |
| fake_repost_share_decisions | oracle | combined | 9.821 | 0.149 | 0.111 | 0.187 | 1.518 |
| fake_repost_share_decisions | oracle | credibility | 9.821 | 0.010 | 0.002 | 0.017 | 0.099 |
| fake_repost_share_decisions | oracle | informational | 9.821 | 0.019 | 0.011 | 0.027 | 0.195 |
| fake_repost_share_decisions | imperfect_fast | combined | 28.575 | 9.097 | 8.753 | 9.441 | 31.836 |
| fake_repost_share_decisions | imperfect_fast | credibility | 28.575 | 2.201 | 2.030 | 2.371 | 7.702 |
| fake_repost_share_decisions | imperfect_fast | informational | 28.575 | 3.243 | 3.062 | 3.423 | 11.348 |
| fake_repost_share_decisions | imperfect_delayed | combined | 22.826 | 15.131 | 14.724 | 15.538 | 66.290 |
| fake_repost_share_decisions | imperfect_delayed | credibility | 22.826 | 2.361 | 2.210 | 2.511 | 10.342 |
| fake_repost_share_decisions | imperfect_delayed | informational | 22.826 | 3.598 | 3.390 | 3.806 | 15.764 |
| target_share_decisions | disabled | combined | 1.822 | 2.257 | 2.055 | 2.458 | 123.854 |
| target_share_decisions | disabled | credibility | 1.822 | 0.642 | 0.558 | 0.727 | 35.261 |
| target_share_decisions | disabled | informational | 1.822 | 1.026 | 0.919 | 1.134 | 56.329 |
| target_share_decisions | discovery | combined | 0.566 | 3.618 | 3.265 | 3.972 | 639.360 |
| target_share_decisions | discovery | credibility | 0.566 | 0.859 | 0.716 | 1.002 | 151.789 |
| target_share_decisions | discovery | informational | 0.566 | 1.603 | 1.426 | 1.780 | 283.276 |
| target_share_decisions | flag_only | combined | 16.406 | 14.986 | 14.466 | 15.505 | 91.341 |
| target_share_decisions | flag_only | credibility | 16.406 | 4.525 | 4.314 | 4.736 | 27.580 |
| target_share_decisions | flag_only | informational | 16.406 | 6.299 | 6.000 | 6.597 | 38.392 |
| target_share_decisions | oracle | combined | 0.222 | 0.253 | 0.193 | 0.313 | 114.052 |
| target_share_decisions | oracle | credibility | 0.222 | 0.033 | 0.023 | 0.042 | 14.818 |
| target_share_decisions | oracle | informational | 0.222 | 0.051 | 0.036 | 0.065 | 22.780 |
| target_share_decisions | imperfect_fast | combined | 13.670 | 20.328 | 19.723 | 20.932 | 148.699 |
| target_share_decisions | imperfect_fast | credibility | 13.670 | 4.981 | 4.677 | 5.284 | 36.433 |
| target_share_decisions | imperfect_fast | informational | 13.670 | 7.249 | 6.940 | 7.558 | 53.029 |
| target_share_decisions | imperfect_delayed | combined | 6.987 | 31.228 | 30.732 | 31.723 | 446.937 |
| target_share_decisions | imperfect_delayed | credibility | 6.987 | 4.829 | 4.583 | 5.075 | 69.113 |
| target_share_decisions | imperfect_delayed | informational | 6.987 | 7.337 | 7.003 | 7.671 | 105.005 |

## RQ3: receiver-scale sensitivity under the imperfect-fast policy

| Outcome | Policy | Strategy | Control (%) | Effect (pp) | CI low | CI high | Relative (%) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| fake_repost_share_decisions | scale0_25 | combined | 28.171 | 11.152 | 10.741 | 11.563 | 39.587 |
| fake_repost_share_decisions | scale0_25 | credibility | 28.171 | 2.401 | 2.198 | 2.604 | 8.522 |
| fake_repost_share_decisions | scale0_25 | informational | 28.171 | 3.586 | 3.346 | 3.825 | 12.728 |
| fake_repost_share_decisions | scale1_0 | combined | 29.697 | 5.215 | 5.009 | 5.420 | 17.560 |
| fake_repost_share_decisions | scale1_0 | credibility | 29.697 | 1.731 | 1.605 | 1.858 | 5.829 |
| fake_repost_share_decisions | scale1_0 | informational | 29.697 | 2.444 | 2.299 | 2.589 | 8.229 |
| target_share_decisions | scale0_25 | combined | 12.899 | 24.969 | 24.309 | 25.628 | 193.577 |
| target_share_decisions | scale0_25 | credibility | 12.899 | 5.385 | 5.058 | 5.712 | 41.748 |
| target_share_decisions | scale0_25 | informational | 12.899 | 7.946 | 7.594 | 8.298 | 61.604 |
| target_share_decisions | scale1_0 | combined | 15.796 | 11.797 | 11.453 | 12.141 | 74.684 |
| target_share_decisions | scale1_0 | credibility | 15.796 | 3.924 | 3.705 | 4.143 | 24.842 |
| target_share_decisions | scale1_0 | informational | 15.796 | 5.559 | 5.297 | 5.821 | 35.192 |

## RQ4: five highest Morris influences within each outcome and strategy

| Outcome | Strategy | Factor | mu-star (pp) | sigma (pp) | n |
| --- | --- | --- | --- | --- | --- |
| fake_repost_share_decisions | combined | memory_half_life | 0.806 | 1.429 | 80 |
| fake_repost_share_decisions | combined | target_reach | 0.599 | 1.053 | 80 |
| fake_repost_share_decisions | combined | base | 0.539 | 1.534 | 80 |
| fake_repost_share_decisions | combined | contacts | 0.531 | 0.817 | 80 |
| fake_repost_share_decisions | combined | wom_receiver_scale | 0.374 | 0.973 | 80 |
| fake_repost_share_decisions | credibility | base | 0.204 | 0.737 | 80 |
| fake_repost_share_decisions | credibility | source_attribute_contrast | 0.150 | 0.521 | 80 |
| fake_repost_share_decisions | credibility | unknown_fake_probability | 0.150 | 0.982 | 80 |
| fake_repost_share_decisions | credibility | wom_receiver_scale | 0.148 | 0.698 | 80 |
| fake_repost_share_decisions | credibility | target_reach | 0.133 | 0.265 | 80 |
| target_share_decisions | combined | memory_half_life | 1.435 | 2.403 | 80 |
| target_share_decisions | combined | target_reach | 1.012 | 1.745 | 80 |
| target_share_decisions | combined | contacts | 0.886 | 1.348 | 80 |
| target_share_decisions | combined | base | 0.736 | 1.470 | 80 |
| target_share_decisions | combined | wom_receiver_scale | 0.615 | 1.480 | 80 |
| target_share_decisions | credibility | base | 0.274 | 0.763 | 80 |
| target_share_decisions | credibility | memory_half_life | 0.217 | 0.458 | 80 |
| target_share_decisions | credibility | target_reach | 0.173 | 0.343 | 80 |
| target_share_decisions | credibility | source_attribute_contrast | 0.154 | 0.541 | 80 |
| target_share_decisions | credibility | unknown_fake_probability | 0.095 | 0.592 | 80 |

## RQ4: structural sensitivity

| Outcome | Topology | Activity | Strategy | Control (%) | Effect (pp) | CI low | CI high | Relative (%) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| fake_repost_share_decisions | directed small-world | 0.25 | combined | 30.626 | 0.338 | 0.229 | 0.447 | 1.104 |
| fake_repost_share_decisions | directed small-world | 0.25 | credibility | 30.626 | 0.063 | -0.029 | 0.154 | 0.204 |
| fake_repost_share_decisions | directed small-world | 0.50 | combined | 26.380 | 1.165 | 0.987 | 1.344 | 4.418 |
| fake_repost_share_decisions | directed small-world | 0.50 | credibility | 26.380 | 0.196 | 0.032 | 0.360 | 0.745 |
| fake_repost_share_decisions | directed small-world | 1.00 | combined | 10.543 | 0.355 | 0.249 | 0.461 | 3.367 |
| fake_repost_share_decisions | directed small-world | 1.00 | credibility | 10.543 | -0.009 | -0.041 | 0.023 | -0.083 |
| fake_repost_share_decisions | random fixed-outdegree | 0.25 | combined | 30.649 | 0.337 | 0.237 | 0.436 | 1.098 |
| fake_repost_share_decisions | random fixed-outdegree | 0.25 | credibility | 30.649 | 0.060 | -0.037 | 0.157 | 0.197 |
| fake_repost_share_decisions | random fixed-outdegree | 0.50 | combined | 24.185 | 1.549 | 1.225 | 1.872 | 6.403 |
| fake_repost_share_decisions | random fixed-outdegree | 0.50 | credibility | 24.185 | 0.324 | -0.003 | 0.652 | 1.341 |
| fake_repost_share_decisions | random fixed-outdegree | 1.00 | combined | 10.288 | 0.706 | -0.200 | 1.612 | 6.863 |
| fake_repost_share_decisions | random fixed-outdegree | 1.00 | credibility | 10.288 | 0.017 | -0.073 | 0.107 | 0.167 |
| target_share_decisions | directed small-world | 0.25 | combined | 18.901 | 0.859 | 0.728 | 0.989 | 4.543 |
| target_share_decisions | directed small-world | 0.25 | credibility | 18.901 | 0.161 | 0.048 | 0.273 | 0.850 |
| target_share_decisions | directed small-world | 0.50 | combined | 14.132 | 2.188 | 2.005 | 2.372 | 15.485 |
| target_share_decisions | directed small-world | 0.50 | credibility | 14.132 | 0.415 | 0.297 | 0.534 | 2.939 |
| target_share_decisions | directed small-world | 1.00 | combined | 1.204 | 0.577 | 0.433 | 0.722 | 47.971 |
| target_share_decisions | directed small-world | 1.00 | credibility | 1.204 | 0.051 | 0.010 | 0.093 | 4.254 |
| target_share_decisions | random fixed-outdegree | 0.25 | combined | 19.008 | 0.930 | 0.777 | 1.082 | 4.891 |
| target_share_decisions | random fixed-outdegree | 0.25 | credibility | 19.008 | 0.191 | 0.095 | 0.288 | 1.005 |
| target_share_decisions | random fixed-outdegree | 0.50 | combined | 12.981 | 2.441 | 2.230 | 2.652 | 18.804 |
| target_share_decisions | random fixed-outdegree | 0.50 | credibility | 12.981 | 0.496 | 0.183 | 0.809 | 3.819 |
| target_share_decisions | random fixed-outdegree | 1.00 | combined | 1.183 | 0.654 | 0.273 | 1.034 | 55.269 |
| target_share_decisions | random fixed-outdegree | 1.00 | credibility | 1.183 | 0.070 | 0.008 | 0.132 | 5.952 |
